# JMeter Allure reporting
- Are you writing functional tests in JMeter?
- Is it difficult to understand what went wrong using standard means?
- Want to see detailed test reports?
- Do you need historicity?
- Need to keep test documentation?

This tutorial will help you create reports on JMeter tests in Allure Report format

NOT USABLE for load testing

##  Quick start via docker-compose
```bash
docker compose up && \
docker cp jmeter:/result/allure-results allure-results-example &&\
docker rm -f jmeter &&\
allure generate allure-results-example --clean -o allure-report
```
## Quick start via GUI-Mode
```bash
chmod +x installer.sh
./installer.sh
./apache-jmeter/bin/jmeter -t allure-jmeter-example.jmx 
```
Then you should change in User Defined Variables two variables to absolute path and click RUN

_ALLURE_REPORT_PATH: 
 - To run it via docker-compose just put '/result/allure-results'
 - To run local put here the absolute path to folder allure-results

_ALLURE_CONFIG_PATH: 
 - To run it via docker-compose just put allure-reporter.groovy (This file is already there in the root)
 - To run local put here the absolute path to file allure-reporter.groovy 

## How it works:
First, you need to initialize the parameters that are required to generate test results:
![Optional Text](images/user_defined_variables.png)

If the case consists of several steps, it should look like this:
![Optional Text](images/multiple_steps_case.png)

Declare annotations before first step:
![Optional Text](images/multiple_steps_case_declare_annotations.png)

Declare parameters in JSR223 Assertion:
![Optional Text](images/groovy_parameters_in_multiple.png)

For example, if the case consists of one step a validation check looks like this:
![Optional Text](images/single_step_cases.png)

Do not use parameters if case has one step:
![Optional Text](images/groovy_parameters_in_signle.png)

Default report looks like this:
![Optional Text](images/jmeter_view_result_tree.png)
- After running the script will create files in the folder allure-results for generating the report
, next just generate Allure Report with command:
```bash
allure generate allure-results --clean -o allure-report
```

Allure Report would look like this:
![Optional Text](images/allure-report.png)


## Works with the following Assertions:

| Assertion                         |
|--------------------               |
| Response Assertion                |
| JSON Assertion                    |
| Size Assertion                    |
| jp@gc - JSON/YAML Path Assertion  |

It should also work with the rest of default assertions.

## UPD:
If you would like to ignore case, just add 'skipped' to parameters near 'stop':
![Optional Text](images/skipped_parameter.png)
![Optional Text](images/allure-report-skipped-case.png)

If you have critical main case to prepare data for next tests, you may use 'critical' parameters.
If this case will fail - all next tests (thread group) will be stopped.
![Optional Text](images/critical_parameter.png)

If you want to add tags - do like this
![Optional Text](images/tags_parameter.png)
![Optional Text](images/allure-report-tags.png)

## Troubleshooting:
- In test case with multiple steps if you forget to add one of the parameters in one of the steps, 
this step won't make it to the report and something will definitely go wrong, so be careful!
