#!/bin/bash
#
# Command line statements which use the RVF API to test a simple refset
#
# Stop on error
set -e;
#set -x;  #echo every statement executed
#
# Declare parameters
fileToTest="rel2_Refset_SimpleDelta_INT_20140131.txt"

# Target API Deployment
api="http://localhost:8080/api/v1"
#api="http://localhost:8081/api/v1"
#api="https://uat-rvf.ihtsdotools.org/api/v1"

#TODO make this function miss out the data if jsonFile is not specified.
function callURL() {
  httpMethod=$1
  url=$2
  jsonFile=$3

jsonData=`cat ${jsonFile}`   #Parsing the json to objects in spring seems very forgiving of white space and unescaped characters.   
   curl -i \
    --header "Content-type: application/json" \
    --header "Accept: application/json" \
    -X ${httpMethod} \
    -d "${jsonData}" \
     ${url}
}

echo
echo "Target Release Validation Framework API URL is '${api}'"
echo

echo "Creating a new Assertion"
callURL POST ${api}/assertions json_files/create_assertion.json
echo

read -p "Continue? (y/n): " user_choice
case "$user_choice" in
  n|N) echo "Calling a halt to the proceedings"; exit 0;;
  *) echo 'OK, pressing on...';;
esac

echo "Return list of all assertions"
curl --header "Accept: application/json" ${api}/assertions/
echo

echo "Return assertion with specified id"
curl --header "Accept: application/json" ${api}/assertions/1
echo

echo "Return assertion with specified id as XML"
curl --header "Accept: application/xml" ${api}/assertions/1
echo

echo "Returning assertion with updated name"
callURL PUT ${api}/assertions/1 json_files/update_assertion_name.json
echo

echo "Creating sample tests"
callURL POST ${api}/tests json_files/create_test_1.json
callURL POST ${api}/tests json_files/create_test_2.json
echo

echo "Linking tests with assertion"
callURL POST ${api}/assertions/1/tests json_files/link_tests_to_assertion.json
echo

echo "Getting tests associated with assertion"
curl -i \
  --header "Content-type: application/json" \
  --header "Accept: application/json" \
  -X GET ${api}/assertions/1/tests
echo

echo "Deleting assertion with specified id"
curl -i \
  --header "Content-type: application/json" \
  --header "Accept: application/json" \
  -X DELETE ${api}/assertions/1
#curl -i -X DELETE ${api}/assertions/delete/1
echo

#echo "Deleting assertion with missing id"
#curl -i \
#  --header "Content-type: application/json" \
#  --header "Accept: application/json" \
#  -X DELETE ${api}/assertions/delete/23863232
#echo

echo "Return list of all assertions - should be missing assertion with id 1"
curl --header "Accept: application/json" ${api}/assertions/
echo