/**
 *  Linksys/Cisco Internet Camera Local
 *
 *  Modified example Foscam device type to support dynamic input of credentials
 *  and enable / disable motion to easily integrate into homemade
 *  security systems (when away, mark "motiondecetionStatus" as "on", when present, mark
 *  "motiondecetionStatus" as "off".  For use with email or FTP image uploading built
 *  into Linksys/Cisco cameras.
 */
metadata {
	definition (name: "Linksys & Cisco Internet Camera Local", namespace: "bigpunk6", author: "bigpunk6") {
		capability "Polling"
		capability "Image Capture"
        
        attribute "hubactionMode", "string"
        attribute "motiondecetionStatus", "string"
        
        command "toggleMotiondecetion"
	}
    
    preferences {
        input("ip", "string", title:"Camera IP Address", description: "Camera IP Address", required: true, displayDuringSetup: true)
        input("port", "string", title:"Camera Port", description: "Camera Port", defaultValue: 80 , required: true, displayDuringSetup: true)
        input("username", "string", title:"Camera Username", description: "Camera Username", required: true, displayDuringSetup: true)
        input("password", "password", title:"Camera Password", description: "Camera Password", required: true, displayDuringSetup: true)
        input "size", "enum", title: "Image Resolution", required: true, displayDuringSetup: true,
            options:[1:"160x120",
                     2:"320x240",
                     3:"640x480"]
        input "quality", "enum", title: "Image Quality", required: true, displayDuringSetup: true,
            options:[1:"Very high",
                     2:"High",
                     3:"Normal",
                     4:"Low",
                     5:"Very low"]
}

	tiles {
        carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }
        
        /*standardTile("camera", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "default", label: "", action: "Image Capture.take", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
        }*/

		standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
			state "taking", label:'Taking', action: "", icon: "st.camera.take-photo", backgroundColor: "#53a7c0"
			state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
		}
        
        standardTile("motiondecetionStatus", "device.motiondecetionStatus", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "off", label: "off", action: "toggleMotiondecetion", icon:"st.motion.motion.inactive", backgroundColor: "#bc2323"
            state "on", label: "on", action: "toggleMotiondecetion", icon:"st.motion.motion.active",  backgroundColor: "#79b821"
        }

        standardTile("refresh", "device.motiondecetionStatus", inactiveLabel: false, decoration: "flat") {
          state "refresh", action:"polling.poll", icon:"st.secondary.refresh"
        }

        main "take"
			details(["cameraDetails", "take", "motiondecetionStatus", "refresh"])
	}
}

def parse(String description) {
	log.debug "Parsing2 '${description}'"
    def msg = parseLanMessage(description)
    log.debug msg.header // => headers as a string
    log.debug msg.headers      // => headers as a Map
    log.debug msg.body              // => request body as a string
    log.debug msg.status          // => http status code of the response
    log.debug msg.json              // => any JSON included in response body, as a data structure of lists and maps
    log.debug msg.xml                // => any XML included in response body, as a document tree structure
    log.debug msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

    def map = [:]
    def retResult = []
    def descMap = parseDescriptionAsMap(description)
    log.debug "descMap: ${descMap}"
	if (descMap["bucket"] && descMap["key"]) {
		putImageInS3(descMap)
	}
    else if (descMap["headers"] && descMap["body"]) {
        def body = new String(descMap["body"].decodeBase64())
	}
    
    /*if (msg.headers.'Content-Type'.contains("image/jpeg")) {
        def imageBytes = response.data
        if (imageBytes) {
          storeImage(getPictureName(), imageBytes)
        }
    }*/
}

def parseCameraResponse(def response) {
  if (response.headers.'Content-Type'.contains("image/jpeg")) {
    def imageBytes = response.data
    if (imageBytes) {
      storeImage(getPictureName(), imageBytes)
    }
  } 
}

def take() {
	log.debug("Taking Photo")
	sendEvent(name: "hubactionMode", value: "s3");
    hubGet("/img/snapshot.cgi?size=${size}&quality=${quality}")
}

private hubGet(def apiCommand) {
    def iphex = convertIPtoHex(ip)
    def porthex = convertPortToHex(port)
    device.deviceNetworkId = "$iphex:$porthex"
    log.debug "Device Network Id set to ${iphex}:${porthex}"
	log.debug("Executing hubaction on " + getHostAddress())
    def uri = ""
    //uri = apiCommand + getLogin()
    uri = apiCommand
    log.debug uri
    //def userpassascii = "${username}:${password}"
    //def userpass = "Basic " + userpassascii.encodeAsBase64().toString()
    //def headers = [:]
    //headers.put("HOST", getHostAddress())
    //headers.put("Authorization", userpass)
    //log.debug "Headers are ${headers}"
    def hubAction = new physicalgraph.device.HubAction(
    	method: "GET",
        path: uri,
        headers: [HOST:getHostAddress()]
    )
    if(device.currentValue("hubactionMode") == "s3") {
        hubAction.options = [outputMsgToS3:true]
        sendEvent(name: "hubactionMode", value: "local");
    }
    log.debug "${hubAction}"
	hubAction
}

def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

def putImageInS3(map) {
    log.debug "putImageInS3"
	def s3ObjectContent
	try {
		def imageBytes = getS3Object(map.bucket, map.key + ".jpg")
		if(imageBytes)
		{
			s3ObjectContent = imageBytes.getObjectContent()
			def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
			storeImage(getPictureName(), bytes)
		}
	}
	catch(Exception e) {
		log.error e
	}
	finally {
		//Explicitly close the stream
		if (s3ObjectContent) { s3ObjectContent.close() }
	}
}

def toggleMotiondecetion() {
  if(device.currentValue("motiondecetionStatus") == "on") {
    motiondecetionOff()
  } else {
    motiondecetionOn()
  }
}

private motiondecetionOn() {
    log.debug("Motion Detection changed to: on")
    sendEvent(name: "motiondecetionStatus", value: "on");
    hubGet("/adm/set_group.cgi?group=MOTION&md_mode=1")
}

private motiondecetionOff() {
    log.debug("Motion Detection changed to: off")
    sendEvent(name: "motiondecetionStatus", value: "off");
    hubGet("/adm/set_group.cgi?group=MOTION&md_mode=0")
}

def poll() {
    log.debug "poll"
	sendEvent(name: "hubactionMode", value: "local");
    hubGet("/adm/get_group.cgi?group=MOTION")
}

private getLogin() {
	return "${username}:${password}@"
}

private getPictureName() {
  def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
  "image" + "_$pictureUuid" + ".jpg"
}

private getHostAddress() {
    return "${ip}:${port}"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex

}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}