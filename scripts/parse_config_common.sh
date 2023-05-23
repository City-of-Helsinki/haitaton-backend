# Utility functions are shared between multiple scripts.

yq() {
  docker run --rm -i -v "${PWD}":/workdir mikefarah/yq "$@"
}

parse_config () {
    parsed_value=$(yq eval -e ".${1}" - < $config_yaml) > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        retval="$parsed_value"
    else
        retval=""
    fi
    echo "$retval"
}
