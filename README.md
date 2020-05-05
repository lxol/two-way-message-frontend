
# two-way-message-frontend

## API
| Path                                                | Method | Description                                  |
|:----------------------------------------------------|:-------|----------------------------------------------|
| ```/messages/personal-account```                    | GET    | Redirect to PTA home                         |  
| ```/messages```                                     | GET    | Redirect to PTA messages                     |
| ```/message/:enquiryType/make_enquiry```            | GET    | Displays the 2-way message input form        |
| ```/message/submit```                               | POST   | Sends a customer 2-way message to HMRC       |
| ```/message/customer/:enquiryType/:replyTo/reply``` | GET    | Displays the 2-way message reply input form  | 
| ```/message/customer/:enquiryType/:replyTo/reply``` | POST   | Sends a customer 2-way message reply to HMRC |
--------------------------------------------------------------------------------------------------------------- 
 
 ### GET /message/:enquiryType/make_enquiry
 
 Takes the following parameters, of which, returnLinkUrl and returnLinkText should be encrypted using the query parameter encryption library (https://github.com/hmrc/crypto/blob/master/src/main/scala/uk/gov/hmrc/crypto/ApplicationCrypto.scala#L31):
 
 | Name                 | Description |
 | -------------------- | ----------- |
 | `returnLinkUrl`      | (Optional/Encrypted - url of returnLink) The encrypted URL that the user will be redirected to at the end of any journeys when pressing the "Go back to..." return button |
 | `returnLinkText`     | (Optional/Encrypted - text of returnLink) The encrypted text that will be used to label the "Go back to..." return button |
 | `enquiryType`        | Enquiry type (i.e. "p800", "epaye-jrs", etc.) |
 
 Responds with:
 
 | Status                        | Description |
 | ----------------------------- | ----------- |
 | 412 Precondition failed       | If two-way-message service is unable to provide a message identifier |
 | 200 Ok                        | If successful, the user is presented with an enquiry message creation form|
 
# Before committing
Please run the following commands before committing:
```shell script
sbt fmt
sbt test
sbt it:test
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").





