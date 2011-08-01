
def uri = 'a'
def protocol = 'b'
def query = 'c'
def path = 'd'

showInfo(
	uri:uri,protocol:protocol,query:query,path:path)



def showInfo(info){
			info.each{ k,v ->
			println "$k = $v" 
		}	
	}
