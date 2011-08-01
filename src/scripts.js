// file: scrips.js
// author: jbetancourt
// 
// External JQuery use.
// technique reference:  http://www.latentmotion.com/separating-jquery-functions-into-external-files-without-selectors/
/* <![CDATA[ */
var pingServer;
var stopServer;

(function($){
	pingServer = function(){
		$('#pingButton').click(function(){			
			$.get('/ping', {mode:"ping"},
				function(data){
				    $('#target').append("<br/>"+data) 				
				},"html")
			    .error(function(response,status,xhr){
					var msg = "Server does not responsd: ";
					$("#target").html(msg + " " + status + 
						(xhr.status ? "<br/>xhr.status: [" + xhr.status + "] " 
							 + "] xhr.statusText: [" + xhr.statusText + "]"	
							 : "") 
					);					
				});			
		});
	};
})(jQuery);
			
(function($){ 
	stopServer = function(){
		$('#stopButton').click(function(){
			$("#submitButton").attr('disabled','disabled');
			$("#pingButton").attr('disabled','disabled');
			$("#stopButton").attr('disabled','disabled');
			
			$('#target').load('/stop', function(response,status,xhr){
				if(status == "error"){
					var msg = "Server does not responsd; ";
					$("#target").html(msg + xhr.status + " " + xhr.statusText);					
				}				
			});			
		});
	};
})(jQuery);
			
/* ]]> */
