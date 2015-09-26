/*
    GE/Jasco ZigBee Dimmer
*/

metadata {
	// Automatically generated. Make future change here.
	definition (name: "GE ZigBee Dimmer", namespace: "smartthings", author: "SmartThings") {
		capability "Switch Level"
		capability "Actuator"
		capability "Switch"
		capability "Power Meter"
		capability "Configuration"
		capability "Sensor"
		capability "Refresh"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0B05,0702", outClusters: "000A,0019", manufacturer: "Jasco Products", model: "45852"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0B05,0702", outClusters: "000A,0019", manufacturer: "Jasco Products", model: "45857"
	}

	// simulator metadata
	simulator {
		// status messages
		status "on": "on/off: 1"
		status "off": "on/off: 0"

		// reply messages
		reply "zcl on-off on": "on/off: 1"
		reply "zcl on-off off": "on/off: 0"
	}

	// UI tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 3, inactiveLabel: false, range:"(0..100)") {
			state "level", action:"switch level.setLevel"
		}
        valueTile("level", "device.level", inactiveLabel: false, decoration: "flat") {
			state "level", label:'${currentValue} %', unit:"%", backgroundColor:"#ffffff"
		}
        valueTile("power", "device.power", inactiveLabel: false, decoration: "flat") {
			state "power", label:'${currentValue} Watts'
		}
		main "switch"
		details(["switch", "level", "power", "levelSliderControl", "refresh"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.info description
	if (description?.startsWith("catchall:")) {
		def msg = zigbee.parse(description)
		log.trace "data: $msg.data"
        if(description?.endsWith("0100") ||description?.endsWith("1001") || description?.matches("on/off\\s*:\\s*1"))
        {
            def result = createEvent(name: "switch", value: "on")
            log.debug "Parse returned ${result?.descriptionText}"
            return result
        }
        else if(description?.endsWith("0000") || description?.endsWith("1000") || description?.matches("on/off\\s*:\\s*0"))
        {
            def result = createEvent(name: "switch", value: "off")
            log.debug "Parse returned ${result?.descriptionText}"
            return result
        }
	} 
    else if (description?.startsWith("read attr -")) {
		def descMap = parseDescriptionAsMap(description)
		log.debug "Read attr: $description"
        if(descMap.cluster == "0008"){
            def dimmerValue = convertHexToInt(descMap.value) * 100 / 255 as Integer
            log.debug "dimmer value is $dimmerValue"
            def result = createEvent(name: "level", value: dimmerValue)
            return result
        }
		else if (descMap.cluster == "0006" && descMap.attrId == "0000") {
			name = "switch"
			value = descMap.value.endsWith("01") ? "on" : "off"
            def result = createEvent(name: name, value: value)
            log.debug "Parse returned ${result?.descriptionText}"
            return result
		}
        else if(descMap.cluster =="0702" && descMap.attrId == "0400") {
			def value = convertHexToInt(descMap.value)/10 
            //Dividing by 10 as the Divisor is 10000 and unit is kW for the device. AttrId: 0302 and 0300. Simplifying to 10
            log.debug value
			def name = "power"
            def result = createEvent(name: name, value: value)
			log.debug "Parse returned ${result?.descriptionText}"
			return result

		}
    }
	else {
		def name = description?.startsWith("on/off: ") ? "switch" : null
		def value = name == "switch" ? (description?.endsWith(" 1") ? "on" : "off") : null
		def result = createEvent(name: name, value: value)
		log.debug "Parse returned ${result?.descriptionText}"
		return result
	}
}

def on() {
	log.debug "on()"
	sendEvent(name: "switch", value: "on")
	["st cmd 0x${device.deviceNetworkId} ${endpointId} 6 1 {}"] + meter()
}

def off() {
	log.debug "off()"
	sendEvent(name: "switch", value: "off")
	["st cmd 0x${device.deviceNetworkId} ${endpointId} 6 0 {}"] + meter()
}

def setLevel(value) {
	log.trace "setLevel($value)"
	def cmds = []

	if (value == 0) {
		sendEvent(name: "switch", value: "off")
		cmds << "st cmd 0x${device.deviceNetworkId} ${endpointId} 6 0 {}"
	}
	else if (device.latestValue("switch") == "off") {
        sendEvent(name: "switch", value: "on")
        cmds << "st cmd 0x${device.deviceNetworkId} ${endpointId} 6 1 {}"
        
	}

	sendEvent(name: "level", value: value)
	def level = hex(value * 255/100)
	cmds << "st cmd 0x${device.deviceNetworkId} ${endpointId} 8 4 {${level} 0000}"

	//log.debug cmds
	cmds + meter()
}

def refresh() {

	[
		"st rattr 0x${device.deviceNetworkId} ${endpointId} 6 0", "delay 200",
		"st rattr 0x${device.deviceNetworkId} ${endpointId} 8 0", "delay 200",
		"st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0702 0x0400"
	]

}

def configure() {

	def configCmds = [
    
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 6 {${device.zigbeeId}} {}", "delay 200",
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 8 {${device.zigbeeId}} {}", "delay 200",
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x0702 {${device.zigbeeId}} {}", "delay 200"
	]

	configCmds + refresh()
}

def meter() {
    [
        "delay 3000", "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0702 0x0400"
    ]
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

private hex(value, width=2) {
	def s = new BigInteger(Math.round(value).toString()).toString(16)
	while (s.size() < width) {
		s = "0" + s
	}
	s
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}
