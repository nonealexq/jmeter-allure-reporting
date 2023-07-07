/*
 	Author: Alexey Chichuk
	Description: Groovy for create allure-results for JMeter
	Date Create: 29.07.2021
	Date Update: 07.07.2023
	Version: 1.4.16
*/
	version = '1.4.16'

import java.time.LocalDateTime
import groovy.json.JsonSlurper
import org.apache.jmeter.util.Document
import java.util.regex.Matcher

/*
	Annotations AllureStory и AllureFeature, must be initialized ahead of time before tests or in
	start of controller. For example in JSR223 Sampler:

	vars.put("allure.name","Case name");
	vars.put("allure.description","Case Description");
	vars.put("allure.label.feature","Value of Feature");
	vars.put("allure.label.story","Value of Story");
	vars.put("allure.label.severity","critical");
	vars.put("allure.label.layer","jmeter");
*/

empty = ''

/*
	Init main annotations to empty value
 */
epicNameForFullName = empty; allureStepDisplayName = empty; links = empty
labels = empty; issues = empty; tempVar = empty; stepParameters = empty; mainParameters = empty
timeoutFailureText = 'java.net.SocketTimeoutException: Read timed out'

/*
	Init co-main annotations to empty value
 */
allureCaseFailReason = empty; allureDisplayName = empty; allureCaseDescription = empty; allureStepFailReason = empty; solotest = empty

/*
	Init count variables to empty
 */
summaryCountTests = empty; passedCountTests = empty; failedCountTests = empty; skippedCountTests = empty

/*
	A variable for storing the results of the run must be created in advance
	For example put it in User Defined Variables
	vars.put('_ALLURE_REPORT_PATH','/result/allure-results');
*/
allureReportPath = vars.get('_ALLURE_REPORT_PATH')

/*
	Init variable to logging info
 */
stepLog = '****'
allureLoggerInfo = stepLog + ' org.allure.reporter: '

/*
	Init variables for summary counting
 */
if (props.get('SummaryCountTests') == null){
	summaryCountTests = 0; passedCountTests = 0; failedCountTests = 0; skippedCountTests = 0
} else {
	summaryCountTests = props.get('SummaryCountTests')
	passedCountTests = props.get('PassedCountTests')
	failedCountTests = props.get('FailedCountTests')
	skippedCountTests = props.get('SkippedCountTests')
}

/*
	Init critical variable
*/
critical = vars.get('critical')

stage = 'finished'

if (vars.get('allureCaseFailReason') == null){
	allureCaseFailReason = empty
} else allureCaseFailReason = vars.get('allureCaseFailReason')

/*
	Find request content-type, request and response data. If not found - default text/plain
 */
if (!prev.getRequestHeaders().findAll("[cC]ontent-[tT]ype:?(.*)").toString().contains('[]') && sampler.getClass().getName().contains('HTTPSampler')){
	requestType = prev.getRequestHeaders().findAll("[cC]ontent-[tT]ype:?(.*)").toString().replaceAll('].*',"")
			.replaceAll(';.*',"").replaceAll(".*:","").replaceAll(" ","")
	requestData = sampler.getUrl().toString() + '\n\n' +
			prev.getRequestHeaders().replaceAll(/[aA]uthorization:.*/,"Authorization: XXX (Has been replaced for safety)").
					replaceAll(/[xX]-[aA]pi-[tT]oken:.*/,"X-Api-Token: XXX (Has been replaced for safety)") + '\n' +
						prev.getHTTPMethod() + ":" + '\n' + prev.getQueryString()
} else {
	requestType = 'text/plain'
	requestData = prev.getSamplerData()
}

/*
	If response content-type not getting - set default text/plain
 */
if ( prev.getContentType().replaceAll(";.*","").contains('/') ){
	responseType = prev.getContentType().replaceAll(";.*","")
} else responseType = 'text/plain'

/*
	If we wanna see own content-type - write it force in parameters
 */
if (Parameters.contains('content_type=')) {
	if (Parameters =~ ~/content_type=\[(.+?)]/) {
		def issues_memory = Matcher.lastMatcher[0][1].split(',')
		responseType = issues_memory[0]
	}
}

/*
	Main logic to build .result file of allure format
 */
prevMainSteps = vars.get('prevMainSteps')
allureStepResult = vars.get('allureStepResult')
allureCaseResult = vars.get('allureCaseResult')
responseData = prev.getResponseDataAsString()
SummarySubSteps = empty

/*
	Random UUID for creating attachments and result
	Unique enough so that test results do not overlap
*/
attachUUID 	= UUID.randomUUID().toString()


/*
	Vars for attachments
*/
vars.put('attachment-UUID', attachUUID)
vars.put('pngUUID', attachUUID)

/*
	To use tika lib, for example download and assert something in xlxs/xml just use parameter
	tika_xml (if you have multiples steps, put it with space (for example: 'start tika_xml')
*/
if (Parameters.contains('tika_xml')) {
	byte [] samplerData = ctx.getPreviousResult().getResponseData()
	String converted = Document.getTextFromDocument(samplerData)
	if ((m = (converted =~ /sharedStrings.xml\n(.*)/))) {
		responseData = m[0].toString()
	} else responseData = converted.toString()
	responseType = 'text/plain'
}

/*
	Adding main fields for allure report (display name/ description)
 */
void addMainFieldsFromEnv(){
	vars.entrySet().each { var ->
		if ((var.getKey() =~ 'allure.' || var.getKey() =~ 'Allure') && !solotest){
			if ( (var.getKey().replaceAll('allure.', '') == 'name') ){
				allureDisplayName = var.getValue()
			}
			if (var.getKey().replaceAll('allure.', '') == 'description'){
				allureCaseDescription = var.getValue()
			}
		}
	}
}

/*
	Create main params to case
 */
if (vars.get('allure.parameters') != null) {
	if (vars.get('allure.parameters') =~ ~/\[(.+?)]/) {
		def parametersMemory = Matcher.lastMatcher[0][1].split(',')
		for(int i = 0; i < parametersMemory.size(); i++) {
			tempVar = vars.get(parametersMemory[i])
			mainParameters += '{' +
					'"name":"' + parametersMemory[i] + '",' +
					'"value":"' + tempVar.toString().replaceAll("[\\t\\n\\r]+"," ").replace("\"", "\\\"") + '"' +
					'}'
			// If last param --> no comma
			if (i < parametersMemory.size()-1 ){
				mainParameters += ','
			}
			vars.put('mainParameters', mainParameters)
		}
	}
} else vars.put('mainParameters', empty)

/*
	Adding all labels to allure report
 */
void addAllLabelsFromEnv(){
	vars.entrySet().each { var ->
		if (var.getKey() =~ 'allure.label'){
			labels += '{' +
					'"name":"' + var.getKey().replaceAll('allure.label.', '').replaceAll('Allure','').toLowerCase() + '",' +
					'"value":"' + var.getValue().toString() + '"' +
					'},'
		}
		if (var.getKey().replaceAll('allure.label.', '') == 'epic'){
			epicNameForFullName = var.getValue().toString().toLowerCase().replace(' ', '_') + '.'
		}
	}
}

/*
	Adding all links to allure report
 */
void addAllLinksFromEnv(){
	/*
		Calc index of link hash
	 */
	tempVar = new HashSet(vars.entrySet()); indexHash = 0; indexAddLinks = 0
	for (Iterator iter = tempVar.iterator(); iter.hasNext();) {
		var = iter.next();
		if ( var.getKey().startsWith("allure.link") ) {
			indexHash++
		}
	}

	vars.entrySet().each { var ->
		if (var.getKey() =~ 'allure.link'){
			links += '{' +
					'"name":"' + var.getKey().replaceAll('allure.link.', '')+ '",' +
					'"url":"' + var.getValue().toString() + '"' +
					'}'
			indexAddLinks++
			if (indexHash > indexAddLinks ){
				links += ','
			}
		}
	}
}


/*
	Clear all labels after stop
 */
void clearAllLabelsFromEnv(){
	copy = new HashSet(vars.entrySet());
	for (Iterator iter = copy.iterator(); iter.hasNext();) {
		var = iter.next();
		if ( (var.getKey().startsWith("allure.label") && !solotest) || var.getKey().startsWith("allure.label.AS_ID")) {
			vars.remove(var.getKey());
		}
	}
}

/*
	Clear all labels after stop
 */
void clearAllLinksFromEnv(){
	copy = new HashSet(vars.entrySet());
	for (Iterator iter = copy.iterator(); iter.hasNext();) {
		var = iter.next();
		if ( (var.getKey().startsWith("allure.link") && !solotest)) {
			vars.remove(var.getKey());
		}
	}
}

/*
	Adding one label
 */
void addOneLabel(String labelName, String labelValue){
	labels += '{' +
			'"name":"' + labelName + '",' +
			'"value":"' + labelValue + '"' +
			'},'
}

/*
	Clear all variables after creation report for case
 */
void clearAllureVariable(){
	vars.put('prevMainSteps', empty)
	vars.put('AResult', empty)
	vars.put('SummarySubSteps', empty)
	vars.put('critical', empty)
	vars.put('allureCaseResult', 'passed')
	vars.put('allureCaseFailReason', empty)
	vars.put('issues', empty)
	vars.put('allure.parameters', null)
	vars.put('mainParameters', empty)
	vars.put('loopCounter', null)
	clearAllLabelsFromEnv()
	clearAllLinksFromEnv()
}

/*
	Adding jira issue to allure report
 */
if (Parameters.contains('issues=')) {
	if (Parameters =~ ~/issues=\[(.+?)]/) {
		def issuesMemory = Matcher.lastMatcher[0][1].split(',')
		for(int i = 0; i < issuesMemory.size(); i++) {
			issues += '{' +
					'"name":"issue",' +
					'"value":"' + issuesMemory[i] + '"' +
					'},'
			vars.put('issues', issues)
		}
	}
}
if (vars.get('issues') == empty || vars.get('issues') == null){
		issues = '{' +
				'"name":"issue",' +
				'"value":"' + empty + '"' +
				'},'
		vars.put('issues', issues)
}

/*
	Add parameters to step
 */
if (Parameters.contains('parameters=')) {
	if (Parameters =~ ~/parameters=\[(.+?)]/) {
		def parametersMemory = Matcher.lastMatcher[0][1].split(',')
		for(int i = 0; i < parametersMemory.size(); i++) {
			tempVar = vars.get(parametersMemory[i])
			stepParameters += '{' +
					'"name":"' + parametersMemory[i] + '",' +
					'"value":"' + tempVar.toString().replaceAll("[\\t\\n\\r]+"," ").replace("\"", "\\\"") + '"' +
					'}'
				// If last param --> no comma
				if (i < parametersMemory.size()-1 ){
					stepParameters += ','
				}
		}
	}
} else stepParameters = empty

/*
	Create full name of case for allure history
*/
void buildAllureFullName(){
	allureFullName = 'org.jmeter.com.' + epicNameForFullName + vars.get("allure.label.feature").toString().toLowerCase().replace(' ',
			'_') + '.' + vars.get("allure.label.story").toString().toLowerCase().replace(' ',
			'_') + '.' + vars.get("allure.name").toString().toLowerCase().replace(' ','_')
}

/*
	Func for adding all steps
*/
void addAllSteps() {
	int countAssertions = prev.getAssertionResults().size().toInteger()
	vars.putObject("countAssertions", countAssertions)

	for (i = 0; i < countAssertions; i++) {

		assertionResult = prev.getAssertionResults()[i]

		if (assertionResult.isFailure()) {
			log.info(allureLoggerInfo + (stepLog * i) + '[' + i + '] Step: ' + prev.getAssertionResults()[i].toString()
					+ ': failed; reason: ' + assertionResult.getFailureMessage().toString())

			allureStepDisplayName = prev.getAssertionResults()[i].toString()

			if (responseData.contains(timeoutFailureText)){
				allureStepFailReason = prev.getQueryString().findAll('\"method\"?\"(.*)\"').toString().replace("\"", "") + ' ' + timeoutFailureText
				allureCaseFailReason = allureStepFailReason
			}
			else if (allureCaseFailReason.contains(timeoutFailureText)){
				allureCaseFailReason = allureCaseFailReason
			}
			else {
				allureStepFailReason = assertionResult.getFailureMessage().toString()
				allureMainFailReason = '[Sample: ' + sampler.getName()+ ' in sub step: ' +
						allureStepDisplayName + ' failed with reason: ' + assertionResult
						.getFailureMessage().toString() + ']' + '\\' + 'n'
				allureCaseFailReason = allureCaseFailReason + allureMainFailReason
			}

			vars.put('allureCaseFailReason', allureCaseFailReason)
			allureCaseResult = 'failed'
			vars.put('allureCaseResult', 'failed')
			allureStepResult = 'failed'

			addMoreSubStep()
		}

		if (!assertionResult.isFailure()) {
			log.info(allureLoggerInfo + (stepLog * i) + '[' + i + '] Step: ' + prev
					.getAssertionResults()[i].toString()
					+ ': passed')
			allureStepDisplayName = prev.getAssertionResults()[i].toString()
			allureStepResult = 'passed'
			allureStepFailReason = empty
			addMoreSubStep()
		}
	}
	if (countAssertions == 0) {
		if (responseData.contains(timeoutFailureText)){
			allureCaseFailReason = prev.getQueryString().findAll('\"method\"?\"(.*)\"').toString().replace("\"", "") + ' ' + timeoutFailureText
			vars.put('allureCaseFailReason', allureCaseFailReason)
		}
	}
}


/*
	Case is starting
 */

if (( !Parameters.contains('stop') && !Parameters.empty && Parameters.contains('start') )) {
	vars.put('caseTimeStart', prev.getStartTime().toString())
	addAllSteps()
	addMoreMainStep(false)
}

/*
	Case continue
*/

else if ( (Parameters.contains('stop') && !Parameters.empty || Parameters.contains('continue') )) {
	addAllSteps()
	addMoreMainStep(true)
}

/*
	Case with single step
*/

else if (!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start')) {
	vars.put('allureCaseResult', 'passed')
	vars.put('prevMainSteps', empty)
	vars.put('caseTimeStart',prev.getStartTime().toString())
	allureDisplayName = sampler.getName()
	solotest = true
	/*
		If case is solo step - get description from sampler comment
	 */
	if (sampler.getComment() != null) {
		allureCaseDescription = sampler.getComment()
	} else allureCaseDescription = empty
	addAllSteps()
	addMoreMainStep(false)
}

else {
	throw new Exception ("ERROR: Oops... Something is going wrong")
}

/*
	Func for adding sub steps
*/
def addMoreSubStep(){
	if (!SummarySubSteps.empty) SummarySubSteps = SummarySubSteps + ','
	String SubStep = '{' +
			'"name":"'+ allureStepDisplayName.toString() + '",' +
				'"status":"' + allureStepResult + '",' +
				'"stage":"'+ stage +'",' +
				'"statusDetails":' +
					'{' +
						'"message":"' + allureStepFailReason.replace("\"", "\'").replace("\\", "\\\\").replace("\n", " ").replace("\t", " ")  + '"' +
					'}' +
			'}'

	SummarySubSteps = SummarySubSteps + SubStep
	vars.put('SummarySubSteps',SummarySubSteps)

}

/*
	Func for adding main steps
*/
def addMoreMainStep(boolean addPoint){

	if (SummarySubSteps.contains('"status":"failed"')) allureStepResult = 'failed'
		else allureStepResult = 'passed'

	if (prevMainSteps == null) prevMainSteps = empty
	if (addPoint && prevMainSteps != empty) prevMainSteps = prevMainSteps + ','

	vars.put('attachUUID', attachUUID)
	String StepL = '{' +
			'"name":"' + sampler.getName().replace("\"", "\'") + '",' +
			'"status":"' + allureStepResult + '",' +
			'"stage":"'+ stage +'",' +
			'"steps":' +
				'[' +
					SummarySubSteps +
				'],' +
			'"statusDetails": {"message":"' + empty + '"},' +
			'"attachments":' +
				'[' +
					'{' +
						'"name":"Request",' +
						'"source":"' + vars['attachUUID'] + '-request-attachment",' +
						'"type":"'+ requestType +'"' +
					'},' +
					'{' +
						'"name":"Response",' +
						'"source":"' + vars['attachUUID'] + '-response-attachment",' +
						'"type":"'+ responseType +'"' +
					'}' +
			'],' +
			'"parameters":' +
				'[' +
					stepParameters +
				'],' +
			'"start":"' + prev.getStartTime().toString() + '",' +
			'"stop":"' + prev.getEndTime().toString() + '"' +
			'}'

	prevMainSteps =  prevMainSteps + StepL

/*
	If one of step is failed = case is failed
*/
	if (prevMainSteps.contains('"status":"failed"')) {
		allureCaseResult = 'failed'
		if (!Parameters.contains('skipped')) {
			failedCountTests += 1
		}
	} else {
		allureCaseResult = 'passed'
		if (!Parameters.contains('skipped')) {
			passedCountTests += 1
		}
	}

	if (Parameters.contains('skipped')) {
		allureCaseResult = 'skipped'
		skippedCountTests += 1
	}

	if (Parameters.contains('critical') && allureStepResult == 'failed'){
		vars.put('critical', 'yes')
		critical = 'yes'
	}

	addAllLabelsFromEnv()
	addAllLinksFromEnv()
	addMainFieldsFromEnv()
	buildAllureFullName()

	String AResult = empty +
			'{"name":"' + allureDisplayName.replace("\"", "\'") + '",' +
			'"description":"' + allureCaseDescription.replace("\"", "\'") + '",' +
			'"status":"' + allureCaseResult + '",' +
			'"statusDetails":' +
				'{' +
					'"message":"' + allureCaseFailReason.replace("\"", "\'").replace("\n", " ").replace("\t", " ")  + '"' +
				'},' +
			'"stage":"' + stage + '",' +
			'"steps":' +
				'[' +
					prevMainSteps +
				'],' +
			'"start":' + vars.get('caseTimeStart') + ',' +
			'"stop":' + prev.getEndTime()+',' +
			'"uuid":"' + attachUUID+'","historyId":"' + attachUUID + '",' +
			'"fullName":"' + allureFullName + '",' +
			'"parameters":[' +
				vars.get('mainParameters') +
			'],' +
			'"labels":[' +
					labels +
					vars.get('issues') +
					'{' +
						'"name":"host",' +
						'"value":"' + prev.getThreadName().toString() + '"' +
					'}' +
			'],' +
			'"links":[' +
					links +
				']}'

	vars.put('prevMainSteps', prevMainSteps)
	vars.put('AResult', AResult)
}


/*
	Write attachments to files (request/response)
*/
if(!Parameters.contains('no_report')){
	var request = new PrintWriter(allureReportPath + '/' + attachUUID + '-request-attachment')
	request.write(requestData)
	request.close()

	var response = new PrintWriter(allureReportPath + '/' + attachUUID + '-response-attachment')
	if ( responseType != 'image/png' && !sampler.getClass().getName().contains('JSR223Sampler')){
		response.write(responseData)
	} else response.write(prev.getResponseMessage())
	response.close()
}

/*
	Loop hunter. Via > 100 steps in test - log WARN to console
 */
loopFailureText = 'org.jmeter.com.LoopException: test ' + allureFullName + ' is looped. More then 100 steps.'
if (vars.get('loopCounter') == null){
	loopCounter = 1
	vars.putObject('loopCounter', loopCounter)
} else {
	loopCounter = vars.get('loopCounter').toInteger()
	loopCounter += 1
	vars.putObject('loopCounter', loopCounter)
}

if (loopCounter > 100){
	println(empty + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']'  + '\t[thread: ' +
			prev.getThreadName().toString().toLowerCase() + ']\t' + '\t['+ loopFailureText + ' step: ' + sampler.getName().replace("\"", "\'")+']')
}

/*
	Write result to file if case  end
*/
if ((!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start') && !Parameters.contains('no_report')) ||
		Parameters.contains('stop')  && !Parameters.contains('no_report')) {

	var result = new PrintWriter(allureReportPath  +'/' + attachUUID + '-result.json')
	result.write(vars.get('AResult'))
	result.close()

	if (summaryCountTests == 0){
		println(('====' * 20) + '\n' + (' ' * 30) + "Allure-reporter: v" + version + '\n' + ('====' * 20))
	}
	println(empty + LocalDateTime.now() + '\tINFO' + '\t[test: ' + summaryCountTests + ']' + '\t[thread: ' +
			prev.getThreadName().toString().toLowerCase() + ']\t' + '\t[' + allureFullName + ']: [status: ' + allureCaseResult.toUpperCase() + ']')

	/*
		Check valid JSON of result
	 */
	def slurper = new JsonSlurper()
	try {
		def slurperJsonResult = slurper.parseText(vars.get('AResult'))
	} catch (ex) {
		println(empty + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[JSON Validate failed. Input data same error]')
		println(empty + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[JSON]\t' + vars.get('AResult'))
		println "\n${ex.message}"
	}

	/*
		If critical - stop thread
	 */
	if (critical == 'yes') {
		println(empty + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[Critical test case failed. Stopping thread]')
		prev.setStopThread(true)
	}

	summaryCountTests += 1
	clearAllureVariable()

	props.put('SummaryCountTests', summaryCountTests)
	props.put('PassedCountTests', passedCountTests)
	props.put('FailedCountTests', failedCountTests)
	props.put('SkippedCountTests', skippedCountTests)
		
	log.info("************ SummaryCountTests: " + summaryCountTests)
	log.info("************ PassedCountTests: " + passedCountTests)
	log.info("************ FailedCountTests: " + failedCountTests)
	log.info("************ SkippedCountTests: " + skippedCountTests)
	log.info("************ Success Rate: " + passedCountTests/summaryCountTests * 100 + " %")

}