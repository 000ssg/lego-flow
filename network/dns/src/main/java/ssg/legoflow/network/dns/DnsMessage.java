package ssg.legoflow.network.dns;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a DNS message.
 * 
 * DNS (Domain Name System) messages are structured with a header and variable 
 * sections (questions, answers, authorities, additional).
 * 
 * @since 0.1.0
 */
public class DnsMessage {
    
    private final int transactionId;
    private final boolean isResponse;
    private final int opcode;
    private final boolean authoritativeAnswer;
    private final boolean truncated;
    private final boolean recursionDesired;
    private final boolean recursionAvailable;
    private final int rcode;
    private final List<DnsQuestion> questions;
    private final List<DnsRecord> answers;
    private final List<DnsRecord> authorities;
    private final List<DnsRecord> additional;
    
    public DnsMessage(int transactionId, boolean isResponse, int opcode, 
                     boolean authoritativeAnswer, boolean truncated, 
                     boolean recursionDesired, boolean recursionAvailable, 
                     int rcode, List<DnsQuestion> questions, 
                     List<DnsRecord> answers, List<DnsRecord> authorities, 
                     List<DnsRecord> additional) {
        this.transactionId = transactionId;
        this.isResponse = isResponse;
        this.opcode = opcode;
        this.authoritativeAnswer = authoritativeAnswer;
        this.truncated = truncated;
        this.recursionDesired = recursionDesired;
        this.recursionAvailable = recursionAvailable;
        this.rcode = rcode;
        this.questions = questions != null ? new ArrayList<>(questions) : new ArrayList<>();
        this.answers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
        this.authorities = authorities != null ? new ArrayList<>(authorities) : new ArrayList<>();
        this.additional = additional != null ? new ArrayList<>(additional) : new ArrayList<>();
    }
    
    public int getTransactionId() {
        return transactionId;
    }
    
    public boolean isResponse() {
        return isResponse;
    }
    
    public int getOpcode() {
        return opcode;
    }
    
    public boolean isAuthoritativeAnswer() {
        return authoritativeAnswer;
    }
    
    public boolean isTruncated() {
        return truncated;
    }
    
    public boolean isRecursionDesired() {
        return recursionDesired;
    }
    
    public boolean isRecursionAvailable() {
        return recursionAvailable;
    }
    
    public int getRcode() {
        return rcode;
    }
    
    public List<DnsQuestion> getQuestions() {
        return questions;
    }
    
    public List<DnsRecord> getAnswers() {
        return answers;
    }
    
    public List<DnsRecord> getAuthorities() {
        return authorities;
    }
    
    public List<DnsRecord> getAdditional() {
        return additional;
    }
    
    @Override
    public String toString() {
        return "DnsMessage{" +
                "transactionId=" + transactionId +
                ", isResponse=" + isResponse +
                ", opcode=" + opcode +
                ", questions=" + questions.size() +
                ", answers=" + answers.size() +
                '}';
    }
}
