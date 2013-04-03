#shortcut to boot JBossAS with CapeDwarf configuration
#if you need to set other boot options, please use standalone.sh

DIRNAME=`dirname "$0"`

#check if we need to run bytecode transformation
if ! ls ${DIRNAME}/../modules/com/google/appengine/main/old-appengine-api-1.0-sdk* &> /dev/null; then
    ${DIRNAME}/capedwarf-bytecode.sh
fi

${DIRNAME}/standalone.sh -c standalone-capedwarf.xml
