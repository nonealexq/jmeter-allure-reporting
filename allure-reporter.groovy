/*
 	Author: Alexey Chichuk
	Description: Groovy for create allure-results for JMeter
	Date Create: 29.07.2021
	Date Update: 24.09.2021
*/

import org.apache.jmeter.util.Document;

/*
	Annotations AllureStory и AllureFeature, must be initialized ahead of time before tests or in
	start of controller. For example in JSR223 Sampler
	vars.put('AllureFeature','It is Allure Feature');
	vars.put('AllureStory','It is Allure Story');
*/

allureStory = vars['AllureStory']
allureFeature = vars['AllureFeature']

/*
	A variable for storing the results of the run must be created in advance
	For example put it in User Defined Variables
	vars.put('_ALLURE_REPORT_PATH','/result/allure-results');
*/

allureReportPath = vars['_ALLURE_REPORT_PATH']

/*
	Declaring variables to create correct steps
*/

empty = ''

if (vars['allureCaseFailReason'] == null){
	allureCaseFailReason = empty
}
else allureCaseFailReason = vars['allureCaseFailReason']

stage = 'finished'
type = 'application/json'

AllurePrevMainSteps = vars['AllurePrevMainSteps']
allureStepResult = vars['allureStepResult']
allureCaseResult = vars['allureCaseResult']
responseData = prev.getResponseDataAsString()
stepLog = '****'
allureLoggerInfo = stepLog + ' org.allure.reporter: '
SummarySubSteps = empty

/*
	Random UUID for creating attachments and result
	Unique enough so that test results do not overlap
*/

attachUUID 	= UUID.randomUUID().toString()

/*
	If this is a test with a request to the database, then we take the entire context, if it is HTTP, then we only take the request
	Serves in order not to sacrifice domain cookies on the entire network and not to store them anywhere
*/

if (sampler.getClass().getName().contains('JDBC')){
	requestData = prev.getSamplerData()
} else requestData = sampler.getUrl().toString() + '\n' + prev.getQueryString();

if (prev.getResponseDataAsString().contains('!doctype') || prev.getResponseDataAsString().contains('!DOCTYPE')){
	type = 'text/html'
}

/*
	To use tika lib, for example download and assert something in xlxs/xml just use parameter
	tika_xml (if you have multiples steps, put it with space (for example: 'start tika_xml')
*/

if (Parameters.contains('tika_xml')) {
	byte [] samplerdata = ctx.getPreviousResult().getResponseData()
	String converted = Document.getTextFromDocument(samplerdata)
	if ((m = (converted =~ /sharedStrings.xml\n(.*)/))) {
  		responseData = m[0].toString()
		}
}

if (Parameters.empty){
	allureDisplayName = sampler.getName()
} else allureDisplayName = vars['AllureCaseName']

/*
	Create full name of case for allure history
*/
allureFullName = 'org.jmeter.com.' + allureFeature.toString().toLowerCase().replace(' ',
		'_') + '.' + allureStory.toString().toLowerCase().replace(' ',
		'_') + '.' + allureDisplayName.toString().toLowerCase().replace(' ','_')

/*
	Func for adding all steps
*/
void addAllSteps() {
	int countAssertions = SampleResult.getAssertionResults().size().toInteger();
	vars.putObject("countAssertions", countAssertions)

	for (i = 0; i < countAssertions; i++) {

		assertionResult = SampleResult.getAssertionResults()[i]
		allureStepDisplayName = SampleResult.getAssertionResults()[i].toString();

		if (assertionResult.isFailure()) {
			log.info(allureLoggerInfo+ (stepLog * i) + '[' + i + '] Step: ' + SampleResult
					.getAssertionResults()[i].toString() + ': failed; reason: '
					+ assertionResult.getFailureMessage().toString())
			allureStepFailReason = assertionResult.getFailureMessage().toString()
			allureMainFailReason = '[Sample: ' + sampler.getName()+ ' in sub step: ' +
					allureStepDisplayName + ' failed with reason: ' + assertionResult
					.getFailureMessage().toString() + ']' + '\\' + 'n'

			allureCaseFailReason = allureCaseFailReason + allureMainFailReason
			vars.put('allureCaseFailReason', allureCaseFailReason + allureStepFailReason)
			allureCaseResult = 'failed'
			vars.put('allureCaseResult', 'failed')
			allureStepResult = 'failed'
			addMoreSubStep()
		}

		if (!assertionResult.isFailure()) {
			log.info(allureLoggerInfo+ (stepLog * i) + '[' + i + '] Step: ' + SampleResult
					.getAssertionResults()[i].toString()
					+ ': passed')
			allureStepResult = 'passed'
			allureStepFailReason = empty
			addMoreSubStep()
		}
	}
}

/*
	Case is starting
 */

if (( !Parameters.contains('stop') && !Parameters.empty && Parameters.contains('start') )) {
	vars.put('caseTimeStart',prev.getStartTime().toString())
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

else if ((Parameters.empty)){
	vars.put('allureCaseResult', 'passed')
	vars.put('AllurePrevMainSteps', empty)
	vars.put('caseTimeStart',prev.getStartTime().toString())
	addAllSteps()
	addMoreMainStep(false)
}

else {
	throw new Exception ("ERROR: Oops... Something is going wrong")
};

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
						'"message":"' + allureStepFailReason.replace("\"", "\'").replace("\\", "\\\\").replace("\n", " ")  + '"' +
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

	if (AllurePrevMainSteps == null) AllurePrevMainSteps = empty
	if (addPoint == true && AllurePrevMainSteps != empty) AllurePrevMainSteps = AllurePrevMainSteps +
			','

	vars.put('attachUUID', attachUUID)
	String StepL = '{' +
			'"name":"' + sampler.getName() + '",' +
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
						'"type":"'+ type +'"' +
					'},' +
					'{"name":"Response",' +
					'"source":"' + vars['attachUUID'] + '-response-attachment",' +
					'"type":"' + type + '"' +
					'}' +
			'],' +
			'"start":"' + prev.getStartTime().toString() + '",' +
			'"stop":"' + prev.getEndTime().toString() + '"' +
			'}'

	AllurePrevMainSteps =   AllurePrevMainSteps + StepL

/*
	If one of step is failed = case is failed
*/

	if (AllurePrevMainSteps.contains('"status":"failed"')) allureCaseResult = 'failed'
		else allureCaseResult = 'passed'

	String AResult = empty +
			'{"name":"' + allureDisplayName + '",' +
			'"status":"' + allureCaseResult + '",' +
			'"statusDetails":' +
				'{' +
					'"message":"'+ allureCaseFailReason.replace("\"", "\'")  + '"' +
				'},' +
			'"stage":"' + stage + '",' +
			'"steps":' +
					'[' +
						AllurePrevMainSteps +
					'],' +
			'"start":' + vars['caseTimeStart'] + ',' +
			'"stop":' + prev.getEndTime()+',' +
			'"uuid":"' + attachUUID+'","historyId":"' + attachUUID + '",' +
			'"fullName":"' + allureFullName + '",' +
			'"labels":[' +
					'{' +
						'"name":"framework",' +
						'"value":"jmeter"' +
					'},' +
					'{' +
						'"name":"language",' +
						'"value":"java"' +
					'},'+
					'{' +
						'"name":"story",' +
						'"value":"' + allureStory + '"' +
					'},' +
					'{' +
						'"name":"feature",' +
						'"value":"' + allureFeature + '"' +
				'}' +
			'],' +
			'"links":[]}'

	vars.put('AllurePrevMainSteps', AllurePrevMainSteps)
	vars.put('AResult', AResult)
}

/*
	Write attachments to files (request/response)
*/

var request = new PrintWriter(allureReportPath + '/' + attachUUID + '-request-attachment')
	request.write(requestData)
	request.close()

var response = new PrintWriter(allureReportPath + '/' + attachUUID + '-response-attachment')
	response.write(responseData);
	response.close()

/*
	Write result to file if case  end
*/

if (Parameters.empty || Parameters.contains('stop')) {
	println('ALLURE_CASE_RESULT: ' + allureFullName + ': ' + allureCaseResult.toUpperCase())
	var result = new PrintWriter(allureReportPath + '/' + attachUUID + '-result.json')
	result.write(vars['AResult'])
	result.close()
	vars.put('AllurePrevMainSteps', empty)
	vars.put('AResult', empty)
	vars.put('SummarySubSteps', empty)
	vars.put('allureCaseResult', 'passed')
	vars.put('allureCaseFailReason', empty)
}
