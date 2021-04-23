package server.reader;

import java.nio.ByteBuffer;

import server.frame.PM;



public class PMReader implements Reader<PM> {

    private enum State {DONE,WAITINGLOGIN, WAITINGLOGINTRGT, WAITINGMESSAGE,ERROR};

    private final StringReader reader = new StringReader();
    private State state = State.WAITINGLOGIN;
    private PM msg = new PM();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
    	switch (state) {
	    	case WAITINGLOGIN :
	    		switch(reader.process(bb)) {
		    		case DONE :
		    			msg.setLoginSender(reader.get());
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
		    			msg.setLoginTarget(reader.get());
		    			reader.reset();
		    			state = State.WAITINGMESSAGE;
		    			break;
		    		case ERROR : 
		    			state = State.ERROR;
		    			return ProcessStatus.ERROR;
		    		case REFILL :
		    			return ProcessStatus.REFILL;
		    			
	    		}
	    	case WAITINGMESSAGE : 
	    		switch (reader.process(bb)) {
	    			case DONE : 
	    				msg.setStr(reader.get());
	    				state = State.DONE;
	    				return ProcessStatus.DONE;
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
    public PM get() {
    	if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return msg;
    }

    @Override
    public void reset() {
        state= State.WAITINGLOGIN;
        reader.reset();
    }
}
