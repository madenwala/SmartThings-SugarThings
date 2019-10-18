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
    section() {
        input "speakers", "capability.audioNotification", title: "Audio Devices", multiple: true
        input "isMuted", "boolean", title: "Mute"
        input "isPaused", "boolean", title: "Pause Automations"
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
    state.lastMessageID = 0
    state.nextMessageDate = new Date() - 1;
    subscribe(app, appHandler)
    runEvery1Minute(refreshData)
}

def appHandler(evt) {
    log.debug "Sugarmate - App Event ${evt.value} received"
    def data = getData();    
    def message = getMessage(data);
    speakMessage(message);
}

def refreshData(){
	if(isPaused.equals(true)) {
    	// Do nothing while paused
        log.debug "Sugarmate - Paused"
    } else {
        // Check how old data is and then if old, get data
        def nowDate = new Date();
        def refreshDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", state.nextMessageDate);
        if(refreshDate < nowDate) {
        	def data = getData();
            def message = getMessage(data);
            speakMessage(message);
        } else {
            double totalSeconds = (refreshDate.time - nowDate.time) / 1000;
            int minutes = (totalSeconds - (totalSeconds % 60)) / 60;    
            double seconds = totalSeconds % 60;
        	log.debug "Sugarmate - No refresh data for another ${minutes} minute(s) ${seconds.round(0)} second(s)"    
        }
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
            	state.nextMessageDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", resp.data.timestamp) + 330.seconds;
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
	if(data == null){
    	return "No data from SugarMate";
    }
    
    if(state.lastMessageID != data.x) {
    	log.debug "Sugarmate - Updated data x:${data.x} was previously x:${state.lastMessageID}";
        state.lastMessageID = data.x;
    } else {
    	log.debug "Sugarmate - getMessage with repeat data x:${data.x}";
    }   
    
    if(data.reading.contains('[OLD]')){
    	// TODO make counter based on preference
        return "${personName}'s data is now ${convertTimespanToMinutes(data)} minutes old. Last reading was ${data.value} ${trendWords[data.trend_words]}"
    } else {
    }

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
    def minutesAgo = convertTimespanToMinutes(data);
	def message = "${personName} is ${data.value} ${trendWords[data.trend_words]}";
    if(minutesAgo >= 2) {
    	message = message + " from ${minutesAgo} minutes ago";
    }    
    return message;
}

def speakMessage(message) {
    if(isMuted != "true" && message) {
    	log.debug "Sugarmate - Speaking: " + message;
        speakers.speak(message)
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

def sendNotification(message){
    if (sendPush && message) {
        sendPush(message)
    }
}