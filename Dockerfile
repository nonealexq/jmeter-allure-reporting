FROM openjdk:14-alpine

ENV JMETER_HOME="/opt/apache-jmeter/bin"
ENV JMETER_LIB="/opt/apache-jmeter/lib"
ENV TEST_FRAGMENTS_FOLDER="/mnt/jmeter/test-plan/test_fragments/"
ENV PATH $PATH:${JMETER_HOME}
ENV J_VERSION="5.6.2"

ENV	JMETER_DOWNLOAD_URL="https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${J_VERSION}.tgz"

RUN apk add --no-cache \
	sudo \
	bash \
	curl \
    tzdata \
    jq \
    python3 \
    py3-pip

#Default TZ +5:00 UTC
ENV TZ Asia/Yekaterinburg

COPY jmeter /tmp/
COPY allure-reporter.groovy /tmp/allure-reporter.groovy

RUN curl -L --silent ${JMETER_DOWNLOAD_URL} -k > /tmp/apache-jmeter-${J_VERSION}.tgz \
	&& tar -zxvf /tmp/apache-jmeter-${J_VERSION}.tgz -C /opt \
	&& mv /opt/apache-jmeter-${J_VERSION} /opt/apache-jmeter \
	&& cp -rf /tmp/allure-reporter.groovy /opt/allure-reporter.groovy \
	&& cp -a /tmp/lib/. ${JMETER_LIB} \
    && rm ${JMETER_LIB}/tika-core-1.28.5.jar \
    && rm ${JMETER_LIB}/tika-parsers-1.28.5.jar  \
	&& cp -rf /tmp/jmeter.properties ${JMETER_HOME} \
	&& sed -i -e "s%#includecontroller.prefix=%includecontroller.prefix=${TEST_FRAGMENTS_FOLDER}%g" \
		${JMETER_HOME}/jmeter.properties \
	&& sudo rm -rf /tmp/*

RUN chmod +x ${JMETER_HOME}/jmeter.sh && \
	chmod +x ${JMETER_HOME}/*

WORKDIR ${JMETER_HOME}
