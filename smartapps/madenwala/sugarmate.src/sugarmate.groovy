/**
 *  Sugarmate
 *
 *  Copyright 2019 Mohammed Adenwala
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Sugarmate",
    namespace: "madenwala",
    author: "Mohammed Adenwala",
    description: "Sugarmate for SmartThings retrieves your GCM data and allows automations based on your GCM data.",
    category: "My Apps",
    iconUrl: "https://sugarmate.io/assets/sugarmate-bf45d10bb97dfbe9587c0af8efc3e048ec35f7414d125b2af9f3132fd0e363a4.png",
    iconX2Url: "https://sugarmate.io/assets/sugarmate-bf45d10bb97dfbe9587c0af8efc3e048ec35f7414d125b2af9f3132fd0e363a4.png",
    iconX3Url: "https://sugarmate.io/assets/sugarmate-bf45d10bb97dfbe9587c0af8efc3e048ec35f7414d125b2af9f3132fd0e363a4.png")

import groovy.time.TimeCategory;
import groovy.time.*;

preferences {
    section("external") {
        href(name: "hrefNotRequired",
             title: "Sugarmate Website",
             required: false,
             style: "external",
             url: "https://sugarmate.io/home/settings",
             description: "Retrieve your personal Sugarmate External Json URL by logging into your account. Once authenticated, go to Menu > Settings and scroll to the External JSON section and copy and paste your URL below.")
        input "personName", "text", required: true, title: "Your Name"
        input "jsonUrl", "text", required: true, title: "Sugarmate External Json URL"
    }
    section("Automations") {
        input "isMuted", "boolean", title: "Mute"
        input "isPaused", "boolean", title: "Pause Automations"
    }
    section("Speakers") {
        input "speakers", "capability.audioNotification", title: "Audio Devices", multiple: true
        input "speakersNight", "capability.audioNotification", title: "Audio Devices for Night Mode", multiple: true
    }
    section("No Data") {
    	input "skipNoDataRefresh", "number", title: "Minutes to wait until next notification", range: "0..60", defaultValue: 0
    }
    section("Double-Arrow Down") {
    	input "thresholdDoubleDown", "number", title: "GCM level below for notification", range: "0..400", defaultValue: 150
    	input "skipDoubleDownRefresh", "number", title: "Minutes to wait until next notification", range: "0..60", defaultValue: 0
    }
    section("Single-Arrow Down") {
    	input "thresholdSingleDown", "number", title: "GCM level below for notification", range: "0..400", defaultValue: 100
    	input "skipSingleDownRefresh", "number", title: "Minutes to wait until next notification", range: "0..60", defaultValue: 0
    }
    section("Urgent-Low") {
    	input "thresholdTooLow", "number", title: "Set the level at which you have symptoms of low blood sugar", range: "40..100", defaultValue: 70
    	input "skipTooLowRefresh", "number", title: "Minutes to wait until next notification", range: "0..60", defaultValue: 0
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}


def initialize() {
	state.OLD_MESSAGE = "[OLD]"
	state.forceMessage = false
    state.nextMessageDate = new Date();
    
    state.noDataCount = 0;
    state.doubleDownCount = 0;
    state.singleDownCount = 0;
    state.tooLowCount = 0;
    
    subscribe(app, appHandler)
    runEvery1Minute(refreshData)
}

def appHandler(evt) {
	try {
        log.debug "Sugarmate - App Event ${evt.value} received"
    	def data = getData();    
    	def message = getDefaultMessage(data, false)
    	audioSpeak(message)
    }
    catch(ex) {
    	log.error "Sugarmate - Could not retrieve data: " + ex;
    }
}

def refreshData(){
	try {
        if(isPaused.equals(true)) {
            // Do nothing while paused
            log.debug "Sugarmate - Paused"
        } else {
            // Check how old data is and then if old, get data
            Date nowDate = new Date();
            Date refreshDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", state.nextMessageDate);
            if(refreshDate < nowDate) {
                log.info "Sugarmate - Refresh data..."   
                def data = getData();
                def message = getMessage(data);
                audioSpeak(message);
            } else {
                double totalSeconds = (refreshDate.time - nowDate.time) / 1000;
                int minutes = (totalSeconds - (totalSeconds % 60)) / 60;    
                double seconds = totalSeconds % 60;
                log.debug "Sugarmate - No refresh data for another ${minutes} minute(s) ${seconds.round(0)} second(s) Next: ${state.nextMessageDate} Current: ${nowDate}"; 
            }
        }
    }
    catch(ex) {
    	log.error "Sugarmate - Could not refresh data: " + ex;
    }
}

def getData() {

    log.debug "Sugarmate - Refresh Data from ${jsonUrl}"

    def params = [
        uri: jsonUrl,
        contentType: 'application/json'
    ]
    
	httpGet(params) { resp ->
        if(resp.status == 200){
            // get the data from the response body
            log.debug "Sugarmate - Data: ${resp.data}"
            use(TimeCategory) {
            	if(resp.data.reading.contains(state.OLD_MESSAGE))
                	state.nextMessageDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", state.nextMessageDate) + 325.seconds
                else
                	state.nextMessageDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", resp.data.timestamp) + 325.seconds                    
                def nowDate = new Date()
                log.debug "Sugarmate - NEXT REFRESH: ${state.nextMessageDate}  CURRENT: ${nowDate}"
            } 
            return resp.data;
        } else {
            // get the status code of the response
            log.debug "Sugarmate Status Code: ${resp.status}"
            return null;
        }  
	}
}

def getMessage(data) {
	
	if(data == null)
    	return "No data from Sugarmate";
    String message = null;
    
    // TODO move to global variables
    def trendWords = [
        "NONE":"",
        "NOT_COMPUTABLE":"",
        "OUT_OF_RANGE":"",
        "DOUBLE_UP":"double-arrow up", 
        "SINGLE_UP":"up", 
        "FORTY_FIVE_UP":"slight up", 
        "FLAT":"steady", 
        "FORTY_FIVE_DOWN":"slight down", 
        "SINGLE_DOWN":"down", 
        "DOUBLE_DOWN":"double-arrow down"
    ];
    
    if(data.reading.contains(state.OLD_MESSAGE)) {
    	state.forceMessage = true;
    	// TODO make counter based on preference 
        def returnMessage = skipNoDataRefresh == 0 || state.noDataCount % (skipNoDataRefresh + 1) == 1;
        state.noDataCount = state.noDataCount + 1;
        log.debug "noDataCount: " + state.noDataCount;
        if(returnMessage) 
        	message = "No data from ${personName} for the last ${convertTimespanToMinutes(data)} minutes. Last reading was ${data.value} ${trendWords[data.trend_words]}."
        else 
        	message = "";
    } else {
        if(state.forceMessage && state.noDataCount > 0) {
        	state.forceMessage = false;
    		message = "Data restored. " + getDefaultMessage(data, false);
        }
    	state.noDataCount = 0;
    }

	if(data.trend_words == "DOUBLE_DOWN" && data.value <= thresholdDoubleDown) {
    	state.forceMessage = true;
    	def returnMessage = skipDoubleDownRefresh == 0 || data.reading.contains(state.OLD_MESSAGE) || state.doubleDownCount % (skipDoubleDownRefresh + 1) == 0;
        state.doubleDownCount = state.doubleDownCount + 1;
        log.debug "doubleDownCount: " + state.doubleDownCount;
        if(returnMessage)
            message = "DOUBLE DOWN ALERT! " + getDefaultMessage(data, true);
    } else {
    	state.doubleDownCount = 0;
    }
    
    if(data.trend_words == "SINGLE_DOWN" && data.value <= thresholdSingleDown) {
    	def returnMessage = skipSingleDownRefresh == 0 || data.reading.contains(state.OLD_MESSAGE) || state.singleDownCount % (skipSingleDownRefresh + 1) == 0;
        state.singleDownCount = state.singleDownCount + 1;
        log.debug "singleDownCount: " + state.singleDownCount;
        if(returnMessage)
            message = "SINGLE DOWN ALERT! " + getDefaultMessage(data, true);
    } else {
    	state.singleDownCount = 0;
    }
    
    if(message == null && data.value <= thresholdTooLow && data.delta < 0) {
    	def returnMessage = skipTooLowRefresh == 0 || data.reading.contains(state.OLD_MESSAGE) || state.tooLowCount % (skipTooLowRefresh + 1) == 0;
        state.tooLowCount = state.tooLowCount + 1;
        log.debug "tooLowCount: " + state.tooLowCount;
        if(returnMessage)
            message = "" + getDefaultMessage(data, true);
    } else {
    	state.tooLowCount = 0;
    }
    
    if(message == null && state.forceMessage) {
    	state.forceMessage = false;
        log.debug "ForceMessage is true. Message is=" + message;
    	message = getDefaultMessage(data, false);
    }
    
	if(message == null) {
        //message = getDefaultMessage(data, false);
    }

    return message;
}

def getDefaultMessage(data, showDelta) {
    // TODO move to global variables
    def trendWords = [
        "NONE":"",
        "NOT_COMPUTABLE":"",
        "OUT_OF_RANGE":"",
        "DOUBLE_UP":"double-arrow up", 
        "SINGLE_UP":"up", 
        "FORTY_FIVE_UP":"slight up", 
        "FLAT":"steady", 
        "FORTY_FIVE_DOWN":"slight down", 
        "SINGLE_DOWN":"down", 
        "DOUBLE_DOWN":"double-arrow down"
    ];
    
    // TODO send the appropriate response based on above
    //return "${personName} is ${data.value} ${trendWords[data.trend_words]} as of ${data.time}";
    String message = null;
    def minutesAgo = convertTimespanToMinutes(data);
    message = "${personName} is ${data.value} ${trendWords[data.trend_words]}";
    if(showDelta && data.delta < 0)
    	message = message + " ${data.delta}";
    if(minutesAgo >= 2)
        message = message + " from ${minutesAgo} minutes ago";
    return message;
}

def audioSpeak(message) {
    log.info "Sugarmate - Message: " + message;
    if(isMuted != "true" && message) {
    	log.debug "Sugarmate - Audio Speak: " + message;
        //speakers.playAnnouncementAll(message);
        //speakers.playTextAndRestore(message);
        
        if(location.mode == 'Night')
            speakersNight.playTextAndRestore(message);
        else
            speakers.playTextAndRestore(message);
    }
}

def convertTimespanToMinutes(data) {
    // Parse the data timestamp
    def dataDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", data.timestamp);
    def nowDate = new Date();
    // Convert milliseconds to seconds
    double seconds = (nowDate.time - dataDate.time) / 1000;
    // Round sounds down to the minute(s)
    int minutes = (seconds - (seconds % 60)) / 60;
    // Return the minutes
    return minutes;
}

/*
def sendNotification(message){
    if (sendPush && message)
        sendPush(message)
}
*/