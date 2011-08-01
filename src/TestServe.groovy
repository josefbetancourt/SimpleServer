//import static main.SimpleServer as SS;   // still won't work!!! http://jira.codehaus.org/browse/GROOVY-4386
import com.sun.net.httpserver.HttpExchange;
import main.SimpleServer

main.SimpleServer.serve(0){ 
	ss, t, params ->
	
	if(!(params.size())){
		def path = t.getRequestURI().getRawPath()
	
		if(path.length()>1){
				ss.sendPage(t,path)
		}else if(path =="/") {
				ss.sendPage(t,"/src/index.html")
		}	
	}else{	
		def answer = params.get("reply")
		reply = (!answer || answer[0] != '"42"') ? "Wrong!" : "Correct!"
			
		ss.sendString(t,"<h1>$reply</h1>")
		ss.stopServer()
	}
}