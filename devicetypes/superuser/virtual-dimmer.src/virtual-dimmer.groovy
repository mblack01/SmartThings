metadata {
        definition (name: "Virtual Dimmer", namespace: "", author: "smartthings") {
        capability "Switch"
        capability "Refresh"
        capability "Switch Level"
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
	tiles {
		standardTile("button", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: 'Off', action: "switch.on", icon: "st.Kids.kid10", backgroundColor: "#ffffff", nextState: "on"
			state "on", label: 'On', action: "switch.off", icon: "st.Kids.kid10", backgroundColor: "#79b821", nextState: "off"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}        
        controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, backgroundColor:"#ffe71e") {
            state "level", action:"switch level.setLevel"
        }
        valueTile("lValue", "device.level", inactiveLabel: true, height:1, width:1, decoration: "flat") {
            state "levelValue", label:'${currentValue}%', unit:"", backgroundColor: "#53a7c0"
        }

		main(["button"])
		details(["button", "refresh","levelSliderControl","lValue"])
	}
}

def parse(String description) {
}

def on() {
	sendEvent(name: "switch", value: "on")
    log.info "Dimmer On"
}

def off() {
	sendEvent(name: "switch", value: "off")
    log.info "Dimmer Off"
}

def setLevel(val){
    log.info "setLevel $val"
    
    // make sure we don't drive switches past allowed values (command will hang device waiting for it to
    // execute. Never commes back)
    if (val < 0){
    	val = 0
    }
    
    if( val > 100){
    	val = 100
    }
    
    if (val == 0){ // I liked that 0 = off
    	sendEvent(name:"level",value:val)
    	off()
    }
    else
    {
    	on()
    	sendEvent(name:"switch.setLevel",value:val) // had to add this to work if apps subscribed to
                                                    // setLevel event. "Dim With Me" was one.
        sendEvent(name:"level",value:val)
    }
}

def refresh() {
    log.info "refresh"
}