#shortcut to boot JBossAS with CapeDwarf configuration
#if you need to set other boot options, please use standalone.sh

DIRNAME=`dirname "$0"`
${DIRNAME}/standalone.sh -c standalone-capedwarf.xml
