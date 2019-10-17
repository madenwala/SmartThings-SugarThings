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
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(app, appHandler)
    runEvery1Minute(refreshData)
}

import groovy.time.TimeCategory;
import groovy.time.*;

def appHandler(evt) {
    log.debug "Sugarmate app event ${evt.name}:${evt.value} received"
    def data = getData();
    
    log.debug convertTimespanToMinutes(data);
    
    def message = getAudioMessage(data);
    speakAudioMessage(message);
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
            log.debug "Sugarmate Data: ${resp.data}"
            return resp.data;
        } else {
            // get the status code of the response
            log.debug "Sugarmate Status Code: ${resp.status}"
            return null;
        }  
	}
}

def speakAudioMessage(message) {
    if(isMuted != "true" && message) {
    	log.debug "Sugarmate - Speaking: " + message;
        speakers.speak(message)
    }
}

def getAudioMessage(data) {
	if(data == null){
    	return "No data from SugarMate";
    }
    
    /*
    if(data.reading.contains('[OLD]')){
    	// TODO make counter based on preference
    	return "No data from Sugarmate for the last ${convertTimespanToMinutes(data)} minutes";
    } else {
    }
    */

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
	return "${personName} is ${data.value} ${trendWords[data.trend_words]} from ${convertTimespanToMinutes(data)} minutes ago";
}

def shouldRefreshData(data){
	if(data == null || data.timestamp)
    	return true;
    def tsStart = Date.parse("yyyy-MM-dd'T'HH:mm:ss", data.timestamp);
    Date tsEnd;
    use(TimeCategory) {
    	tsEnd = tsStart + 310.seconds;
    }
    return timeOfDayIsBetween(tsStart, tsEnd, new Date(), TimeZone.getTimeZone("UTC"));
}

def convertTimespanToMinutes(data) {
	// TODO return (DateTime() - data.timestamp) to Minutes
    def now = new Date();
    def ts = Date.parse("yyyy-MM-dd'T'HH:mm:ss", data.timestamp);
    double mins = (now.time - ts.time) / (60 * 1000);
    int rounded = mins.round(0);
    return rounded;
}

def sendNotification(message){
    if (sendPush && message) {
        sendPush(message)
    }
}

def refreshData(){
	// TODO how to check a boolean value?
	if(isPaused == "true") {
    	// Do nothing while paused
        log.debug "Sugarmate is paused"
    } else {
        // Check how old data is and then if old, get data
        def data;
        if(shouldRefreshData(data)) {
        	data = getData();
        } else {
        	log.debug "Sugarmate - No need to get new data";
        }
        
        // TODO based on value, determine if a message should be sent
        def message = getAudioMessage(data);
        speakAudioMessage(message);
    }
}