/**
 * File: AppContext.groovy
 * Date: 20110320T1952-05:00
 * Author: jbetancourt  
 */

package main

import java.text.DateFormat;
import java.text.SimpleDateFormat

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 *  The context that hosts the application.  
 * 
 * @author jbetancourt
 *
 */
class AppContext  implements HttpHandler{
	def SimpleServer server;
	def static RUNNING = 'running'
	def currentState
	def transitions = [:]

	/**  */
	AppContext(SimpleServer server){
		this.server = server
		currentState = server.getServerProperty("initialState")
		def statesProperty = server.getServerProperty("transitions")
	}

	/**
	 * Handle the given request/response.
	 *
	 * The first response is the input form.  The subsequent
	 * request evaluates the answer if any, and then the server
	 * is stopped.  Any exception will also stop the server.
	 * 
	 */
	@Override
	public void handle(HttpExchange t) throws IOException {
		try{
			def uri = t.getRequestURI()
			def final query = uri.getRawQuery()
			def path = uri.getRawPath()
			def params = server.parseQuery(query)
			showInfo([query:query,uri:uri,path:path,currentState:currentState,params:params])

			def mode = params.get("mode")
			if(atState("running")){
				if(mode){
					if(mode[0] == "mathInput"){
						def reply = params.get("reply")
						evaluateAnswer(t,reply)
						transitionNextState()
					}else if (mode[0]=="ping") {
						server.sendString(t,"huh?")
					}
				}
			}

			if(atState("end")){
				server.stopServer()
			}
		}catch(Exception ex){
			ex.printStackTrace()
			server.stopServer()
			throw ex;
		}
	}

	/**
	* And, send response.
	*/
   def evaluateAnswer(t,answer){
	   def reply
	   try{		   
		   reply = (answer[0] != '"23"') ? "Wrong!" : "Correct!"
	   }catch(Exception ex){
		   reply = "wrong"
	   }

	   server.sendString(t,"<center><h1>$reply</h1></center>")
   }
   
   /**  */
	def showInfo(info){
		println "++++++++ AppContext +++++++++++++++++"
		info.each{ k,v ->
			println k + (v ? ': [' + v +']' : ": []")
		}
		println "-------------------------"
	}

	/**	 	 */
	def ping(t){
		def now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())
		server.sendString(t, "$now")
	}

	/**  */
	def setTransitions(s){
		s.split(",").each{ tran ->
			def kv = tran.split(":")
			transitions.put(kv[0],kv[1])
		}
	}

	/**   */
	def atState(s){
		return currentState == s
	}

	/**   */
	def transitionNextState(){
		print("currentState[$currentState],")
		def ns = transitions[currentState]
		currentState = (ns ? ns : "end")
		println("  next state=[$currentState] ")
	}


} // end AppContext class