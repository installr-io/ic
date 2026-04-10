#!/bin/sh

# create user
if [ $1 -eq 1 ] ; then
    # package install, not upgrade
    USER="${ic-user}"

    echo -n "Creating user '$USER'... "
    id -u $USER &> /dev/null
    if [ $? -eq 0 ] ; then
        echo "User '${USER}' already exists"
    else
        set -e
        useradd --create-home --user-group $USER
        echo "done"
    fi
fi
