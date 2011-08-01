/**
 * File: SimpleServer.groovy
 * Date: 20110314T2125-05:00
 * Author: jbetancourt
 */

package main

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.nio.channels.*
import java.nio.*
import java.text.SimpleDateFormat
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit as TU;
import java.util.zip.*;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Authenticator.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.BasicAuthenticator;
import java.util.concurrent.ScheduledExecutorService;
import org.codehaus.groovy.reflection.ReflectionUtils

import edu.stanford.ejalbert.BrowserLauncher;

/**
 * Example of using the Java 1.6 HTTP server com.sun.net.httpserver.
 *
 * @see "http://download.oracle.com/javase/6/docs/jre/api/net/httpserver/
 * spec/com/sun/net/httpserver/package-summary.html"
 *
 * Version 0.03
 *
 * @author jbetancourt
 */
class SimpleServer implements HttpHandler{
	static final STOP_WAIT = 2
	static final HOST = "localhost"
	static final HTTP_PROTOCOL = "http://"
	static final splashText = " Embedded Java 1.6 HTTP server example (ver $version) "
	static final company = " 20110314T2125-05:00 jbetancourt"
	static final UTF_8 = "UTF-8"
	static final version = "0.3"
	static final realmName = "my realm"
	
	static port =0 // if zero, get unused port
	static url
	static int  counter = 0 // for spinner
	static String basedir   // docbase
	static String scriptDir

	HttpServer server
	static SimpleServer simpleServer
	def SRC_INDEX_HTML = "/src/index.html"
	def serverPropFilePath = "server.properties"
	def props = new Properties()
	def browserLauncher
	def ScheduledExecutorService cancelExec
	def authenticate = false	
	def Closure handleParams
	
	/**
	 * The only thing you really need.
	 * @param port   if 0, unused port will be used
	 * @param closure  handles the request,response
	 * @return
	 */
	static serve(port, Closure closure){
		def ss = new SimpleServer()
		ss.port = port
		ss.handleParams = closure
		ss.serverPropFilePath = ""
		ss.exec(new String[0])
		return ss
	}

	/**
	 * Entry point into the demo.
         * @param args
	 */
	static main(args) {
		splash()
		if(!validJavaVersion()){
			return
		}

		simpleServer = new SimpleServer()
		simpleServer.exec(args)

	} // end main
	
		
	/**  */
	def exec(args){
		try{
			def basedir = new java.io.File(".").getAbsolutePath()
			configure(args)
			createHTTPServer()
			start()
			println("server started. url=$url,basedir=$basedir,scriptDir=$scriptDir,")
	
			def spb = props.getProperty("progressBar")
			cancelExec = Executors.newScheduledThreadPool(1)
			keepAlive(false,
					TU.MILLISECONDS.convert(1L, TU.MINUTES))
		}catch(Exception ex){
			ex.printStackTrace();
		}
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
			def query = uri.getRawQuery()
			def path = uri.getRawPath()
			def params = parseQuery(query)
			showInfo(
				uri:uri,protocol:t.getProtocol(),
				query:query,path:path,params:params)			

			if(handleParams){
				handleParams(this, t, params)
			}else{
				if(query == null){
					if(path.length()>1){
						sendPage(t,path)
					}else if(path =="/") {
						sendPage(t,SRC_INDEX_HTML)
					}
				}
			}			

		}catch(Exception ex){
			ex.printStackTrace()
			stopServer()
			throw ex;
		}
	}

	/**  */
	def showInfo(info){
		println "+++++++ Simple Server ++++++++++++++++++"
		info.each{ k,v ->
			println k + (v ? ': [' + v +']' : ": []")
		}
		println "-------------------------"	

	}

	/**  */
	static splash(){
		println("$splashText\n\n$company")
	}

	/**  */
	def configure(args){
		try{
			basedir = new java.io.File(".").getAbsolutePath()
			scriptDir = "$basedir/src/"
			port  = unusedPort(HOST)
			url = "$HTTP_PROTOCOL$HOST:$port/"

			def propFileName = (args.length) >0 ? args[0] :serverPropFilePath
			if(propFileName){
				def getResource = {def resource->
					ReflectionUtils.getCallingClass(0).
							getResourceAsStream(resource)
				}

				InputStream context = getResource(propFileName)
				if(context){
					props.load(context)
					context.close()
				}
			}	

		}catch(Throwable ex){
			handleException(ex)
		}finally {
			//System.exit(1)
		}
	}

	/**  */
	def createHTTPServer(){
		server = HttpServer.create(new InetSocketAddress(port),0);

		props.propertyNames().iterator().each{key ->
			if(key.matches(".*Context\$")){
				def conf = props.get(key).split(",")
				def className = conf[0]
				def contextPath = conf[1]? conf[1].trim(): "/"

				def obj = createObjectFromScript(className, this)
				def context = server.createContext(contextPath, obj)

				if(conf.length>2){
					def authClassName = conf[2]
					def ac = createObjectFromScript(authClassName,
							conf.length > 3? conf[3] : realmName)

					context.setAuthenticator(ac)
				}

				def iProp = key + ".initialState"
				if(props.containsKey(iProp)){
					obj.currentState = props.getProperty(iProp)
				}

				iProp = key + ".transitions"
				if(props.containsKey(iProp)){
					obj.setTransitions(props.getProperty(iProp))
				}
			}
		}

		def context = server.createContext("/", this)
		if(authenticate){
			context.setAuthenticator(new MyAuthenticator("my realm"))
		}		

		server.createContext("/stop", [
					handle:{
						println("in handler for stop .. t[" + it + "]")
						sendString(it,"stopping server ....")
						stopServer()
					}
				] as HttpHandler);

		server.createContext("/ping", [
					handle:{ ct ->
						println("pinging ...")
						ping(ct);
					}
				] as HttpHandler);

	} // end createHTTPServer

	/**
	 *
	 * @return
	 */
	static boolean validJavaVersion(){
		def flag = true
		def ver = System.getProperty("java.version");
		if(!ver.contains("1.6")  && !ver.contains("1.7")){
			println("ERROR *** Requires Java 1.6 or above. Detected: $ver");
			flag = false;
		}

		return flag;
	}

	/**
	 * Send the initial query page.
	 *
	 * @param t the request handler
	 * @param filePath the html file
	 * @return nothing
	 */
	static sendPage(HttpExchange t, String filePath) throws IOException {
		OutputStream os = null;
		try{
			def fPath = new File(basedir + filePath)
			def uri = fPath.toURI()
			Map<String, List<String>>map = t.getResponseHeaders()
			
			def binary = false

			if(filePath.endsWith( ".js")){
				map.set("Content-Type", "text/javascript; charset=UTF-8")
			}else if(filePath.endsWith(".gif")){
				map.set("Content-Type", "image/gif;")
				binary = true
			}else if (filePath.endsWith(".jpg")) {
				map.set("Content-Type", "image/jpeg;")
				binary = true
			}		

			println("sending .... " + fPath)
			if(binary){
				byte[] bytes = readFile(fPath.getPath())
				t.getResponseHeaders().set("Content-Encoding", "gzip")
				t.sendResponseHeaders(HttpURLConnection.HTTP_OK,0)
				os = new GZIPOutputStream(t.getResponseBody())
				os.write(bytes)
				os.finish()
				t.close()				
			}else{
				def response = fPath.getText()
				t.sendResponseHeaders(HttpURLConnection.HTTP_ACCEPTED, response.length());
				os = t.getResponseBody();
				os.write(response.getBytes());
				t.close()
			}
		}catch(FileNotFoundException ex){
			println(ex.getMessage())
		}catch(Exception ex){
			ex.printStackTrace()
			if(os){
				println("close output stream ...")
				try{
					os.close();
				}catch(Exception ex2){
					ex2.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 
	 * @param path
	 * @return
	 */
	static byte[] readFile(String path){
		File file = new File(path)
		
		FileInputStream inStream = new FileInputStream(file)
		FileChannel inChannel = inStream.getChannel();
		def bb = ByteBuffer.allocate(1024*1024)
		
		while(true){
			int bytesRead = inChannel.read bb
			if(bytesRead == -1){
				break;
			}	
		}
		
		return bb.array()		
	}

	/**
	 * Send the resulting score based on response content.
	 * @param t
	 * @param answer
	 * @return
	 */
	static sendString(t, answer ) throws IOException {
		t.sendResponseHeaders(HttpURLConnection.HTTP_OK, answer.length());
		OutputStream os = t.getResponseBody();
		os.write(answer.getBytes());
		os.close();
	}
	
	/**
	 * Just followed example at:
	 * @see http://download.oracle.com/javase/6/docs/api/java/util
	 * /concurrent/ScheduledExecutorService.html
	 */
	def keepAlive(showProgressBar, Long maxTime){
		def handleBeep = cancelExec.scheduleAtFixedRate(new Runnable(){
					public void run(){
						if(showProgressBar){
							progressBar()
						}
					}

				}, 1, 4, TU.SECONDS);

		cancelExec.schedule(new Runnable(){
					public void run(){
						try{
							println("\ncancel beeping")
							handleBeep.cancel(true)
							println("call stop server in keepAlive")
							stopServer()
							println("call system exit in keepAlive")
							System.exit(0)
						}catch(Exception ex){
						 	ex.printStackTrace();	
						}
					}

				},4, TU.MINUTES);
	}

	/**
	 *  In shell console, ASCII spinner gives visual feedback of running server.
	 *  Got idea for approach at
	 *  @see http://blogs.msdn.com/b/brada/archive/2005/06/11/428308.aspx
	 *  But, then took out the use of a switch.  As Charles Moore would say,
	 *  never use conditionals when it can be calculated.
	 */
	static def progressBar(){
		print("\b${["/","-","\\","-"][counter++ % 4]}")
	}

	/**  */
	Object createObjectFromScript( String className, Object... args ) throws Exception {
		println "Creating $className"
		def gcl = new GroovyClassLoader(this.class.classLoader)
		def path = "$scriptDir${className.replace('.','/')}.groovy"
		def cl = gcl.parseClass( new File(path))
		def ni = cl.newInstance(args)
		return ni;
	}

	/**
	 * Get an unused port for server and browser url.
	 * If port is non-zero.
	 * BTW, at shell:
	 *   On windows:  netstat -an
	 *   On linux: netstat -an | grep -i listen
	 *
	 * @see http://stackoverflow.com/questions/573361
	 * /how-can-i-detect-a-free-port-on-the-server-by-code-from-client-side
	 *
	 * I tried simpler ways, but they didn't work. Like using 0 as port.
	 *
	 * @param hostname
	 * @return port number
	 */
	static int unusedPort(String hostname) throws IOException {
		if(port){
			return port
		}

		def minPort = 8000
		def range = 65536 - 8000;
		
		while (true) {
			int port = minPort + (int) (range * Math.random());
			try {
				Socket s = new Socket(hostname, port);
				s.close(); // is this wise?
			} catch (ConnectException e) {
				return port;
			} catch (IOException e) {
				if (e.getMessage().contains("refused")){
					return port;
				}
				throw e;
			}
		}
	}

	/**	 	 */
	def ping(t){
		def now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())
		sendString(t, "$now")
	}

	/**  */
	String getServerProperty(key){
		return props.getProperty(key)
	}

	/**  */
	def start(){
		server.start();
		launchBrowser(url)
	}

	/**  */
	def launchBrowser(url){
		new BrowserLauncher().openURLinBrowser(url)
	}

	/**  */
	def stopServer(){
		print("\nStopping server ... ")
		((HttpServer)server).stop(2)
		print("stopped!")
		System.exit(0)
	}

	/**  */
	static handleException(Exception ex){
		println("ERROR: ${ex.getMessage()}")
		ex.printStackTrace()
		throw ex
	}

	/**
	 * Parse query into list of values array.
	 *
	 * @see http://stackoverflow.com/questions/1667278/parsing-query-strings-in-java
	 * @param query
	 * @return
	 */
	static Map parseQuery(final String query){
		Map params = new HashMap();

		if(!query || query.length() == 0){
			return params
		}

		def key,val

		for (String param : query.split("&")) {
			String[] pair = param.split("=");

			if(pair.length > 0){
				key = URLDecoder.decode(pair[0], UTF_8);
			}

			val=""
			if(pair.length > 1){
				val = URLDecoder.decode(pair[1], UTF_8);
			}

			List<String> values = params.get(key);
			if (values == null) {
				values = new ArrayList<String>();
				params.put(key, values);
			}
			values.add(!val ? "":val );
		}

		return params;
	}

} // end SimpleServer

// the authenticator class.  Should have been just a simple inner class.
class MyAuthenticator extends BasicAuthenticator {
	/**  */
	public MyAuthenticator(String realm){
		super(realm)
	}

	@Override
	public Authenticator.Result authenticate(HttpExchange t){
		return super.authenticate(t)
	}

	@Override
	public boolean checkCredentials(String username, String password){
		//printf("user=%s, pass=%s%n", username, password)
		return true
	}
} // end MyAuthenticator class
