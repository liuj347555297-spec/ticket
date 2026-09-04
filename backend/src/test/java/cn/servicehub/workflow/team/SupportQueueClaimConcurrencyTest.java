package cn.servicehub.workflow.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import cn.servicehub.workflow.infrastructure.InMemoryTicketWorkflowRepository;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupportQueueClaimConcurrencyTest {
 @Test void onlyOneConcurrentClaimWins() throws Exception {InMemoryTicketWorkflowRepository repo=new InMemoryTicketWorkflowRepository();Instant now=Instant.now();repo.saveTask(new WorkflowTask("00000000-0000-4000-8000-000000000001","TKT-20260901-000001","engine","accept",WorkflowTaskStatus.OPEN,"ROLE_FIRST_LINE_SUPPORT",null,null,null,0,now,now,"QUEUE_A"));int workers=50;var pool=Executors.newFixedThreadPool(10);CountDownLatch ready=new CountDownLatch(workers),start=new CountDownLatch(1),done=new CountDownLatch(workers);AtomicInteger won=new AtomicInteger();for(int i=0;i<workers;i++){String user="user-"+i;pool.submit(()->{ready.countDown();try{start.await();if(repo.claimOpenTask("00000000-0000-4000-8000-000000000001",0,"QUEUE_A",user,Instant.now()))won.incrementAndGet();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{done.countDown();}});}ready.await(5,TimeUnit.SECONDS);start.countDown();done.await(10,TimeUnit.SECONDS);pool.shutdownNow();assertEquals(1,won.get());}
}
