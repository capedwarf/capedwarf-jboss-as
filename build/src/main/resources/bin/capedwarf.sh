#shortcut to boot JBossAS with CapeDwarf configuration
#if you need to set other boot options, please use standalone.sh

DIRNAME=`dirname "$0"`
REALPATH=`cd "$DIRNAME/../bin"; pwd`

#check if we need to run bytecode transformation
if ! ls ${DIRNAME}/../modules/com/google/appengine/main/appengine-api-1.0-sdk-*-capedwarf* &> /dev/null; then
    ${DIRNAME}/capedwarf-bytecode.sh
fi

if [ -z "$1" ]; then
    ${DIRNAME}/standalone.sh -c standalone-capedwarf.xml
else
    cd "$1"
    ${REALPATH}/standalone.sh -c standalone-capedwarf.xml -DrootDeployment=$1
fi
