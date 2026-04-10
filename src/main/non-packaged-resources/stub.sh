#!/bin/bash

JAVA_EXECUTABLE=java
MIN_JAVA_VERSION=${maven.compiler.release}

check_version() {
    local java_cmd=$1
    ## to prevent an empty version when JAVA_TOOL_OPTIONS is set, we grep for the occurrence of "version" first
    local java_version_major=$(${java_cmd} -version 2>&1 | grep "version" | awk -F[\".] 'NR==1{print $2}')
    [ ${java_version_major} -ge ${MIN_JAVA_VERSION} ]
}

find_java() {
    local java_cmd=
    # check $JAVA_HOME
    if [[ -n ${JAVA_HOME} ]]; then
        java_cmd=${JAVA_HOME}/bin/${JAVA_EXECUTABLE}
        if check_version ${java_cmd}; then
            echo ${java_cmd}
            return
        fi
    fi

    # check $PATH
    if [[ -n $(which ${JAVA_EXECUTABLE} 2>/dev/null) ]]; then
        java_cmd=$(which ${JAVA_EXECUTABLE})
        if check_version ${java_cmd}; then
            echo ${java_cmd}
            return
        fi
    fi

    # check alternatives
    if [[ -n $(update-alternatives --list | cut -f1 | tr -d '[:blank:]' | grep -P "^${JAVA_EXECUTABLE}$") ]]; then
        for java_cmd in $(update-alternatives --display ${JAVA_EXECUTABLE} | grep -P '^/' | cut -d' ' -f1); do
            if check_version ${java_cmd}; then
                echo ${java_cmd}
                return
            fi
        done
    fi
}

JAVA_CMD=$(find_java)
if [[ -z ${JAVA_CMD} ]]; then
    # not found
    echo -ne "'${JAVA_EXECUTABLE}' (min. version ${MIN_JAVA_VERSION}) not found. Either put it in your \$PATH"
    echo -ne ", set \$JAVA_HOME\nappropriately or use the 'alternatives system' of your distribution.\n"
    exit 1
fi
exec ${JAVA_CMD} -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar "$0" "$@"
