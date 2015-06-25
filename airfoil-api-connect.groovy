definition(
  name: "Airfoil API Connect",
  namespace: "jnewland",
  author: "Jesse Newland",
  description: "Connect to a local copy of Airfoil API to add and control Airfoil connected Speakers",
  category: "SmartThings Labs",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
  page(name: "config")
}

def config() {
  dynamicPage(name: "config", title: "Airfoil API", install: true, uninstall: true) {

    section("Please enter the details of the running copy of Airfoil API you want to control") {
      input(name: "ip", type: "text", title: "IP", description: "Airfoil API IP", required: true, submitOnChange: true)
      input(name: "port", type: "text", title: "Port", description: "Airfoil API port", required: true, submitOnChange: true)
    }

    if (ip && port) {
      int speakerRefreshCount = !state.speakerRefreshCount ? 0 : state.speakerRefreshCount as int
      state.speakerRefreshCount = speakerRefreshCount + 1
      doDeviceSync()

      def options = getSpeakers() ?: []
      def numFound = options.size() ?: 0

      section("Please wait while we discover your speakers. Select your devices below once discovered.") {
        input name: "selectedSpeakers", type: "enum", required:false, title:"Select Speakers (${numFound} found)", multiple:true, options:options
      }
    }
  }
}

def installed() {
  log.trace "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.trace "Updated with settings: ${settings}"
  unsubscribe()
  initialize()
}

def initialize() {
  // remove location subscription aftwards
  log.debug "INITIALIZE"
  unschedule()

  if (selectedSpeakers) {
    addSpeakers()
  }
  if (ip) {
    runEvery5Minutes("doDeviceSync")
  }
}

def uninstalled() {
  unschedule()
  unsubscribe()
}

def addSpeakers() {
  def speakers = getSpeakers()
  speakers.each { s ->
    def dni = app.id + "/" + s.id
    def d = getChildDevice(dni)
    if(!d) {
      d = addChildDevice("airfoil", "Airfoil Speaker", dni, s?.hub, ["label":s?.name])
      log.debug "created ${d.displayName} with id $dni"
      d.refresh()
    } else {
      log.debug "found ${d.displayName} with id $dni already exists, type: '$d.typeName'"
    }
  }
}

def locationHandler(evt) {
  log.info "LOCATION HANDLER: $evt.description"
  def description = evt.description
  def hub = evt?.hubId

  def parsedEvent = parseEventMessage(description)
  parsedEvent << ["hub":hub]

  if (parsedEvent.headers && parsedEvent.body)
  {
    log.trace "Airfoil API response"
    def headerString = new String(parsedEvent.headers.decodeBase64())
    log.trace "Airfoil API response: ${headerString}"
    def bodyString = new String(parsedEvent.body.decodeBase64())
    log.trace "Airfoil API response: ${bodyString}"
    def body = new groovy.json.JsonSlurper().parseText(bodyString)
    log.trace "Airfoil API response: ${body}"

    if (body.error)
    {
      //TODO: handle retries...
      log.error "ERROR: ${body.error}"
    }
    else if (body instanceof java.util.List)
    { //POST /speakers/*/* response
      def speakers = getSpeakers()
      def speakerParams = [:] << ["hub":hub]
      speakerParams.remove('id')
      if (speakers[body.id]) {
        state.speakers[body.id] << speakerParams
      } else {
        state.speakers[body.id] = speakerParams
      }
    }
    else if (body instanceof java.util.HashMap)
    { //GET /speakers response (application/json)
      def bodySize = body.size() ?: 0
      if (bodySize > 0 ) {
        def speakers = getSpeakers()
        body.each { s ->
          def speakerParams = [:] << s
          speakerParams.remove('id')
          speakerParams << ["hub":hub]
          speakerParams.each { k,v ->
            state.speakers[s.id][k] = v
          }
        }
      }
    }
    else
    {
      //TODO: handle retries...
      log.error "ERROR: unknown body type"
    }
  }
  else {
    log.trace "UNKNOWN EVENT $evt.description"
  }
}

def getSpeakers() {
  state.speakers = state.speakers ?: [:]
}

private def parseEventMessage(Map event) {
  return event
}

private def parseEventMessage(String description) {
  def event = [:]
  def parts = description.split(',')
  parts.each { part ->
    part = part.trim()
    if (part.startsWith('devicetype:')) {
      def valueString = part.split(":")[1].trim()
      event.devicetype = valueString
    }
    else if (part.startsWith('mac:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.mac = valueString
      }
    }
    else if (part.startsWith('networkAddress:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.ip = valueString
      }
    }
    else if (part.startsWith('deviceAddress:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.port = valueString
      }
    }
    else if (part.startsWith('ssdpPath:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.ssdpPath = valueString
      }
    }
    else if (part.startsWith('ssdpUSN:')) {
      part -= "ssdpUSN:"
      def valueString = part.trim()
      if (valueString) {
        event.ssdpUSN = valueString
      }
    }
    else if (part.startsWith('ssdpTerm:')) {
      part -= "ssdpTerm:"
      def valueString = part.trim()
      if (valueString) {
        event.ssdpTerm = valueString
      }
    }
    else if (part.startsWith('headers')) {
      part -= "headers:"
      def valueString = part.trim()
      if (valueString) {
        event.headers = valueString
      }
    }
    else if (part.startsWith('body')) {
      part -= "body:"
      def valueString = part.trim()
      if (valueString) {
        event.body = valueString
      }
    }
  }

  event
}

def doDeviceSync(){
  log.trace "Device Sync!"

  poll()

  if(!state.subscribe) {
    subscribe(location, null, locationHandler, [filterEvents:false])
    state.subscribe = true
  }

}


/////////////////////////////////////
//CHILD DEVICE METHODS
/////////////////////////////////////

// TODO make this handle device id matching like hue-connect parse
def parse(childDevice, description) {
  def parsedEvent = parseEventMessage(description)

  if (parsedEvent.headers && parsedEvent.body) {
    def headerString = new String(parsedEvent.headers.decodeBase64())
    def bodyString = new String(parsedEvent.body.decodeBase64())
    log.debug "parse() - ${bodyString}"
    def body = new groovy.json.JsonSlurper().parseText(bodyString)
    if (body?.id != null)
    { //POST /speakers/*/* response
      def speakers = getChildDevices()
      def d = speakers.find{it.deviceNetworkId == "${app.id}/${body.id}"}
      if (d) {
        if (body.connected) {
          if (body.connected == "true") {
            sendEvent(d.deviceNetworkId, [name: "switch", value: "on"])
          } else {
            sendEvent(d.deviceNetworkId, [name: "switch", value: "off"])
          }
        } else if (body.volume) {
          sendEvent(d.deviceNetworkId, [name: "level", value: body.volume])
        }
      }
    }
    else if (body.error != null)
    {
      //TODO: handle retries...
      log.error "ERROR: application/json ${body.error}"
    }
    else
    { //GET /speakers response (application/json)
      def bodySize = body.size() ?: 0
      if (bodySize > 0 ) {
        def speakers = getChildDevices()
        body.each { s ->
          def d = speakers.find{it.deviceNetworkId == "${app.id}/${body.id}"}
          if (d) {
            if (s.connected == "true") {
              sendEvent(d.deviceNetworkId, [name: "switch", value: "on"])
            } else {
              sendEvent(d.deviceNetworkId, [name: "switch", value: "off"])
            }
            sendEvent(d.deviceNetworkId, [name: "level", value: s.volume])
          }
        }
      }
    }
  } else {
    log.debug "parse - got something other than headers,body..."
    return []
  }
}

def on(childDevice) {
  log.debug "Executing 'on'"
  post("/speakers/${getId(childDevice)}/connect", "")
}

def off(childDevice) {
  log.debug "Executing 'off'"
  post("/speakers/${getId(childDevice)}/disconnect", "")
}

def setLevel(childDevice, percent) {
  log.debug "Executing 'setLevel'"
  def level = Math.round(percent * 100)
  post("/speakers/${getId(childDevice)}/volume", "${level}")
}

private getId(childDevice) {
  return childDevice.device?.deviceNetworkId.split("/")[-1]
}

private poll() {
  def uri = "/speakers"
  log.debug "GET:  $uri"
  sendHubCommand(new physicalgraph.device.HubAction("""GET ${uri} HTTP/1.1
HOST: ${ip}:${port}

""", physicalgraph.device.Protocol.LAN, "${ip}:${port}"))
}

private post(path, text) {
  def uri = "$path"
  def length = text.getBytes().size().toString()

  sendHubCommand(new physicalgraph.device.HubAction("""POST $uri HTTP/1.1
HOST: ${ip}:${port}
Content-Length: ${length}

${text}
""", physicalgraph.device.Protocol.LAN, "${ip}:${port}"))

}
