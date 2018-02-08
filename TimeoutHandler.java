import java.io.IOException;
import java.util.TimerTask;

public class TimeoutHandler extends TimerTask{
	
	private FastFtp instance;
	public TimeoutHandler(FastFtp obj){
		this.instance = obj;
	}
	
	public void run(){
		try {
			//process a timeout
			instance.processTimeout();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
