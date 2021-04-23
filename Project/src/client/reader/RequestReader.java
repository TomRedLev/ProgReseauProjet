package client.reader;

import java.nio.ByteBuffer;


import client.frame.Request;



public class RequestReader implements Reader<Request> {

    private enum State {DONE,WAITINGLOGIN, WAITINGLOGINTRGT, WAITINGMESSAGE,ERROR};

    private final StringReader reader = new StringReader();
    private State state = State.WAITINGLOGIN;
    private Request clients = new Request();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
    	switch (state) {
	    	case WAITINGLOGIN :
	    		switch(reader.process(bb)) {
		    		case DONE :
		    			clients.setLoginRequester(reader.get());
		    			reader.reset();
		    			state = State.WAITINGLOGINTRGT;
		    			break;
		    		case ERROR : 
		    			state = State.ERROR;
		    			return ProcessStatus.ERROR;
		    		case REFILL :
		    			return ProcessStatus.REFILL;
		    			
	    		}
	    	case WAITINGLOGINTRGT :
	    		switch(reader.process(bb)) {
		    		case DONE :
		    			clients.setLoginTarget(reader.get());
		    			reader.reset();
		    			state = State.WAITINGMESSAGE;
		    			break;
		    		case ERROR : 
		    			state = State.ERROR;
		    			return ProcessStatus.ERROR;
		    		case REFILL :
		    			return ProcessStatus.REFILL;
		    			
	    		}
	    	default :
	    		throw new IllegalStateException();
    	}
    }

    @Override
    public Request get() {
    	if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return clients;
    }

    @Override
    public void reset() {
        state= State.WAITINGLOGIN;
        reader.reset();
    }
}
