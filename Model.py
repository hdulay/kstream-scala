class SimpleModel(object):

    def score(self, event):
        print(event)
        # do lda and return highest probability
        return .9

    class Java:
        implements = ["example.PyModel"]

# Make sure that the python code is started first.
# Then execute: java -cp py4j.jar py4j.examples.SingleThreadClientApplication
from py4j.java_gateway import JavaGateway, CallbackServerParameters
simple_model = SimpleModel()
gateway = JavaGateway(
    callback_server_parameters=CallbackServerParameters(),
    python_server_entry_point=simple_model)