package com.spacesim.world;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FleetCommandRetirementTest {
 @Test void historyRemainsExactWhileActiveMembershipAndOrdersCannotReviveAReleasedGroup() {
  var fleet=new FleetId(1);var home=new StarSystemId(1);
  var group=new FleetCommandState.CommandGroupState(1,1,"Former",List.of(fleet),home,false,false,2500);
  var order=new FleetCommandState.FleetOrderState(1,1,FleetCommandState.OrderType.STAGE,
    FleetCommandState.OrderSource.PLAYER,home,List.of(home),0,0,1,FleetCommandState.OrderStatus.STAGING);
  var active=FleetCommandState.empty().addGroup(group).addOrder(order);
  var retired=active.retireGroup(1,FleetCommandState.OrderStatus.FAILED);
  assertTrue(retired.requireGroup(1).retired());assertEquals(group.memberFleetIds(),retired.requireGroup(1).memberFleetIds());
  assertEquals(FleetCommandState.OrderStatus.FAILED,retired.requireOrder(1).status());
  assertEquals(active.nextCommandGroupId(),retired.nextCommandGroupId());assertEquals(active.nextOrderId(),retired.nextOrderId());
  assertEquals(retired,FleetCommandStateCodec.decode(FleetCommandStateCodec.encode(retired)));
  assertEquals(2,java.nio.ByteBuffer.wrap(FleetCommandStateCodec.encode(retired)).getInt(4));
  assertEquals(1,java.nio.ByteBuffer.wrap(FleetCommandStateCodec.encode(active)).getInt(4));
  assertThrows(IllegalArgumentException.class,()->retired.replaceOrder(order));
  var next=retired.addGroup(new FleetCommandState.CommandGroupState(2,1,"Next",List.of(fleet),home,false,false,2500));
  assertEquals(2,next.groups().size());assertFalse(next.requireGroup(2).retired());
  assertEquals(next,FleetCommandStateCodec.decode(FleetCommandStateCodec.encode(next)));
 }
}
