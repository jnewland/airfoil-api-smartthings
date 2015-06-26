metadata {
  definition (name: "Airfoil Speaker", namespace: "airfoil", author: "Jesse Newland") {
    capability "Switch"
    capability "Switch level"
    capability "Refresh"
    command "refresh"
  }

  // UI tile definitions
  tiles {
    standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
      state "off", label: '${name}', action: "switch.on", icon: "st.Electronics.electronics16", backgroundColor: "#ffffff"
      state "on", label: '${name}', action: "switch.off", icon: "st.Electronics.electronics16", backgroundColor: "#79b821"
    }
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
      state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, range:"(0..100)") {
      state "level", action:"switch level.setLevel"
    }
    valueTile("level", "device.level", inactiveLabel: false, decoration: "flat") {
      state "level", label: 'Level ${currentValue}%'
    }

    main(["switch"])
    details(["switch", "levelSliderControl", "refresh"])  }
}

def parse(description) {
  log.debug "parse() - $description"
  def results = []

  def map = description
  if (description instanceof String)  {
    log.debug "stringToMap - ${map}"
    map = stringToMap(description)
  }

  if (map?.name && map?.value) {
    results << createEvent(name: "${map?.name}", value: "${map?.value}")
  }
  results
}

// handle commands
def on() {
  parent.on(this)
  sendEvent(name: "switch", value: "on")
}

def off() {
  parent.off(this)
  sendEvent(name: "switch", value: "off")
}

def setLevel(level) {
  log.debug "Executing 'setLevel'"
  log.debug "level=${level}"
  def percent = level / 100.00
  log.debug "percent=${percent}"
  parent.setLevel(this, percent)
  sendEvent(name: "level", value: level)
}

def refresh() {
  log.debug "Executing 'refresh'"
  parent.poll()
}
