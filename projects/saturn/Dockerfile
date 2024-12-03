FROM openjdk:24

ADD build/distributions/*.tar /opt/

RUN set -eux; \
	microdnf install findutils

EXPOSE 8090

WORKDIR "/opt/config"
CMD ["/bin/sh", "-c", "/opt/saturn-*/bin/saturn"]
