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
  page(name:"mainPage")
}

def mainPage() {
  if (ip) {
    return speakerDiscovery()
  } else {
    return airfoilSelection()
  }
}

def airfoilSelection()
{
  return dynamicPage(name:"airfoilSelection", title:"Airfoil API Config", nextPage:"speakerDiscovery", uninstall: true) {
    section("Please enter the details of the running copy of Airfoil API you want to control") {
      input("ip", "text", title: "IP", description: "Airfoil API IP", required: true)
      input("port", "text", title: "Port", description: "Airfoil API port", required: true)
    }
  }
}

def speakerDiscovery()
{
  int speakerRefreshCount = !state.speakerRefreshCount ? 0 : state.speakerRefreshCount as int
  speaker.speakerRefreshCount = speakerRefreshCount + 1
  def refreshInterval = 3

  def options = speakersDiscovered() ?: []
  def numFound = options.size() ?: 0

  if((speakerRefreshCount % 3) == 0) {
    discoverSpeakers()
  }

  return dynamicPage(name:"speakerDiscovery", title:"Select speakers", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
    section("Please wait while we discover your speakers. Select your devices below once discovered.") {
      input "selectedSpeakers", "enum", required:false, title:"Select Speakers (${numFound} found)", multiple:true, options:options
    }
    section {
      def title = ip ? "Airfoil API running at ${ip}:${port}" : "Connect to Airfoil API"
      href "airfoilSelection", title: title, description: "", state: ip ? "complete" : "incomplete", params: [override: true]
    }
  }
}

private discoverSpeakers() {
  sendHubCommand(new physicalgraph.device.HubAction([
    method: "GET",
    path: "/speakers",
    headers: [
      HOST: "${ip}:${port}"
    ]], ip))
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
  state.subscribe = false

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
    def bodyString = new String(parsedEvent.body.decodeBase64())
    def type = (headerString =~ /Content-type:.*/) ? (headerString =~ /Content-type:.*/)[0] : null
    def body

    if(type?.contains("json"))
    { //(application/json)
      body = new groovy.json.JsonSlurper().parseText(bodyString)

      if (body?.id != null)
      { //POST /speakers/*/* response
        def speakers = getSpeakers()
        def speakerParams = [:] <<
        speakerParams << ["hub":hub]
        speakerParams.remove('id')
        speakers[body.id] = speakerParams
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
          def speakers = getSpeakers()
          body.each { s ->
            def speakerParams = [:] << s
            speakerParams.remove('id')
            speakerParams << ["hub":hub]
            speakerParams.each { k,v ->
              speakers[s.id][k] = v
            }
          }
        }
      }
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
