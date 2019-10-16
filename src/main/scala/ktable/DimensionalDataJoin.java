package ktable;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.scala.Serdes;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

/**
 * POJO
 */
class MyObject {
    // user userid city level
    String user;
    String userid;
    String city;
    String level;

    public MyObject(String user, String userid, String city, String level) {
        this.user = user;
        this.userid = userid;
        this.city = city;
        this.level = level;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "MyObject{" +
                "user='" + user + '\'' +
                ", userid='" + userid + '\'' +
                ", city='" + city + '\'' +
                ", level='" + level + '\'' +
                '}';
    }
}

/**
 * MAIN
 */
public class DimensionalDataJoin {

    public static void main(String[] args) {
        String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";
        String schemaRegistryUrl = args.length > 1 ? args[1] : "http://localhost:8081";
        Properties streamsConfiguration = getProperties(bootstrapServers, schemaRegistryUrl);

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, GenericRecord> clickstream = builder.stream("clickstream");
        KStream<String, GenericRecord> ibmmq = builder.stream("claims");
        KStream<String, GenericRecord> cs = clickstream.map((key, value) -> new KeyValue<>(value.get("username").toString(), value));
        KStream<String, GenericRecord> mq = ibmmq.map((key, value) -> new KeyValue<>(value.get("text").toString(), value));

//        mq.map((k,v) -> /* callout to a web service */ );

        KTable<String, GenericRecord> mqLatestKeyValues = mq.groupByKey().reduce((value1, value2) -> {
            /**
             * Use this method to find the latest value of a key when building the KTable.
             */
            return ((Long)value1.get("timestamp")) > ((Long)value2.get("timestamp")) ? value1 : value2; // TODO: compare timestamps and select latest record
        });

        mqLatestKeyValues.toStream().print(Printed.toSysOut());

        KStream<String, MyObject> joined = cs.join(mqLatestKeyValues,
                (click, mq_user) -> new MyObject(
                    click.get("username").toString(),
                    mq_user.get("text").toString(),
                    click.get("city").toString(),
                    click.get("level").toString())
        );

        joined.map((key, value) -> new KeyValue<>(key, pojoToRecord(value)))
                .to("ibmmq_clickstream_joined");

        KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);

        streams.cleanUp();
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static <T> GenericRecord pojoToRecord(T model) {
        try {
            Schema schema = ReflectData.get().getSchema(model.getClass());

            ReflectDatumWriter<T> datumWriter = new ReflectDatumWriter<>(schema);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            datumWriter.write(model, encoder);
            encoder.flush();

            DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(outputStream.toByteArray(), null);

            return datumReader.read(null, decoder);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Properties getProperties(String bootstrapServers, String schemaRegistryUrl) {
        Properties streamsConfiguration = new Properties();
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run.
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "clickstream-mq-example");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "clickstream-mq-example-client");
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Where to find the Confluent schema registry instance(s)
        streamsConfiguration.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // Specify default (de)serializers for record keys and for record values.
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Records should be flushed every 10 seconds. This is less than the default
        // in order to keep this example interactive.
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);
        return streamsConfiguration;
    }
}
