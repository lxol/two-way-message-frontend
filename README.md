
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
 


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").





