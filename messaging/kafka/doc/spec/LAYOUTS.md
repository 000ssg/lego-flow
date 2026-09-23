# Kafka wire layouts per version (extracted from apache/kafka 3.6.1 message/*.json)

Scope: APIs where version matters for the lego-flow client (negotiated) or the in-memory broker.

Field list = exact per-version body layout, in order. [v] = version gate from the schema.

Generated: `doc/spec/message/*.json` are the authoritative artifacts.

## ProduceRequest v0..v9

### v0
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v1
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v2
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v3
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v4
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v5
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v6
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v7
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v8
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]

### v9
TransactionalId string [3+]
Acks int16 [0+]
TimeoutMs int32 [0+]
TopicData []topicproducedata [0+]


## ProduceResponse v0..v9

### v0
Responses []topicproduceresponse [0+]

### v1
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v2
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v3
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v4
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v5
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v6
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v7
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v8
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]

### v9
Responses []topicproduceresponse [0+]
ThrottleTimeMs int32 [1+]


## FetchRequest v0..v13

### v0
ReplicaId int32 [0-14]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
Topics []fetchtopic [0+]

### v1
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
Topics []fetchtopic [0+]
RackId string [11+]

### v2
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
Topics []fetchtopic [0+]
RackId string [11+]

### v3
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
Topics []fetchtopic [0+]
RackId string [11+]

### v4
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
Topics []fetchtopic [0+]
RackId string [11+]

### v5
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
Topics []fetchtopic [0+]
RackId string [11+]

### v6
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
Topics []fetchtopic [0+]
RackId string [11+]

### v7
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v8
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v9
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v10
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v11
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v12
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]

### v13
ClusterId string [12+]
ReplicaId int32 [0-14]
ReplicaState replicastate [15+]
MaxWaitMs int32 [0+]
MinBytes int32 [0+]
MaxBytes int32 [3+]
IsolationLevel int8 [4+]
SessionId int32 [7+]
SessionEpoch int32 [7+]
Topics []fetchtopic [0+]
ForgottenTopicsData []forgottentopic [7+]
RackId string [11+]


## FetchResponse v0..v12

### v0
Responses []fetchabletopicresponse [0+]

### v1
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v2
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v3
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v4
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v5
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v6
ThrottleTimeMs int32 [1+]
Responses []fetchabletopicresponse [0+]

### v7
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]

### v8
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]

### v9
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]

### v10
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]

### v11
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]

### v12
ThrottleTimeMs int32 [1+]
ErrorCode int16 [7+]
SessionId int32 [7+]
Responses []fetchabletopicresponse [0+]


## JoinGroupRequest v0..v5

### v0
GroupId string [0+]
SessionTimeoutMs int32 [0+]
MemberId string [0+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]

### v1
GroupId string [0+]
SessionTimeoutMs int32 [0+]
RebalanceTimeoutMs int32 [1+]
MemberId string [0+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]

### v2
GroupId string [0+]
SessionTimeoutMs int32 [0+]
RebalanceTimeoutMs int32 [1+]
MemberId string [0+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]

### v3
GroupId string [0+]
SessionTimeoutMs int32 [0+]
RebalanceTimeoutMs int32 [1+]
MemberId string [0+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]

### v4
GroupId string [0+]
SessionTimeoutMs int32 [0+]
RebalanceTimeoutMs int32 [1+]
MemberId string [0+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]

### v5
GroupId string [0+]
SessionTimeoutMs int32 [0+]
RebalanceTimeoutMs int32 [1+]
MemberId string [0+]
GroupInstanceId string [5+]
ProtocolType string [0+]
Protocols []joingrouprequestprotocol [0+]


## JoinGroupResponse v0..v3

### v0
ErrorCode int16 [0+]
GenerationId int32 [0+]
ProtocolName string [0+]
Leader string [0+]
MemberId string [0+]
Members []joingroupresponsemember [0+]

### v1
ErrorCode int16 [0+]
GenerationId int32 [0+]
ProtocolName string [0+]
Leader string [0+]
MemberId string [0+]
Members []joingroupresponsemember [0+]

### v2
ThrottleTimeMs int32 [2+]
ErrorCode int16 [0+]
GenerationId int32 [0+]
ProtocolName string [0+]
Leader string [0+]
MemberId string [0+]
Members []joingroupresponsemember [0+]

### v3
ThrottleTimeMs int32 [2+]
ErrorCode int16 [0+]
GenerationId int32 [0+]
ProtocolName string [0+]
Leader string [0+]
MemberId string [0+]
Members []joingroupresponsemember [0+]


## OffsetCommitRequest v0..v3

### v0
GroupId string [0+]
Topics []offsetcommitrequesttopic [0+]

### v1
GroupId string [0+]
GenerationIdOrMemberEpoch int32 [1+]
MemberId string [1+]
Topics []offsetcommitrequesttopic [0+]

### v2
GroupId string [0+]
GenerationIdOrMemberEpoch int32 [1+]
MemberId string [1+]
RetentionTimeMs int64 [2-4]
Topics []offsetcommitrequesttopic [0+]

### v3
GroupId string [0+]
GenerationIdOrMemberEpoch int32 [1+]
MemberId string [1+]
RetentionTimeMs int64 [2-4]
Topics []offsetcommitrequesttopic [0+]


## OffsetCommitResponse v0..v2

### v0
Topics []offsetcommitresponsetopic [0+]

### v1
Topics []offsetcommitresponsetopic [0+]

### v2
Topics []offsetcommitresponsetopic [0+]


## CreateTopicsRequest v0..v7

### v0
Topics []creatabletopic [0+]
timeoutMs int32 [0+]

### v1
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v2
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v3
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v4
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v5
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v6
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]

### v7
Topics []creatabletopic [0+]
timeoutMs int32 [0+]
validateOnly bool [1+]


## SyncGroupRequest v0..v3

### v0
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]
Assignments []syncgrouprequestassignment [0+]

### v1
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]
Assignments []syncgrouprequestassignment [0+]

### v2
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]
Assignments []syncgrouprequestassignment [0+]

### v3
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]
GroupInstanceId string [3+]
Assignments []syncgrouprequestassignment [0+]


## HeartbeatRequest v0..v3

### v0
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]

### v1
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]

### v2
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]

### v3
GroupId string [0+]
GenerationId int32 [0+]
MemberId string [0+]
GroupInstanceId string [3+]
