package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

public class DealerTest {
    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;    

    @Test
    void SizeQueueHuman(){
        if(dealer.init){
        if(!dealer.queueHuman.isEmpty()){
            int size_dealer = dealer.queueHuman.size();
            dealer.SETHuman();
            assertEquals(size_dealer-1, dealer.queueHuman.size());
        }
    }

    }
    @Test
    void SizeQueue(){
        if(dealer.init){
        if(!dealer.queue.isEmpty()){
            int size_dealer = dealer.queue.size();
            dealer.SET();
            assertEquals(size_dealer-1, dealer.queue.size());
        }
    }

    }
}

