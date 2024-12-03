FROM openjdk:24

ADD build/distributions/pluto-boot-*.tar /opt/
# Requires the contents of ../mercury/build to be copied to build/mercury
ADD build/mercury /opt/mercury
EXPOSE 8080

WORKDIR "/opt/config"
CMD ["/bin/sh", "-c", "/opt/pluto-boot-*/bin/pluto"]
