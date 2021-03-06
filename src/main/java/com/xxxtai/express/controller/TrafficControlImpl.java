package com.xxxtai.express.controller;

import com.xxxtai.express.constant.Command;
import com.xxxtai.express.dao.CacheExecutor;
import com.xxxtai.express.model.*;
import com.xxxtai.express.constant.NodeFunction;
import com.xxxtai.express.toolKit.Common;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
@Scope("prototype")
@Slf4j(topic = "develop")
public class TrafficControlImpl implements TrafficControl {
    private final long ABNORMAL_COST = 180000;
    private LinkedList<Integer> routeNodeList;
    private Node lastLockedNode;
    private Edge lastLockedEdge;
    private Node lockedNode;
    private Edge lockedEdge;
    private Car car;
    private long lockedEdgeStartTime;
    @Resource
    private CacheExecutor cacheExecutor;
    @Resource
    private Graph graph;
    @Resource
    private ResolveDeadLock resolveDeadLock;



    public void isStopToWait(int cardNum, boolean isStart) {
        if (this.routeNodeList == null) {
            log.warn("{}AGV receive cardNum:{} but routeNodeList:{} lastLockedEdge:{} lastLockedNode:{}",
                    car.getAGVNum(), cardNum, routeNodeList, lastLockedEdge, lastLockedNode);
            tryLockEdgeWithoutRoute(cardNum);
            return;
        }

        if (NodeFunction.Junction.equals(graph.getNodeMap().get(cardNum).getFunction())) {
            this.routeNodeList.removeFirst();
            if (lastLockedEdge != null ) {
                long cost = System.currentTimeMillis() - lockedEdgeStartTime;
                if (cost < ABNORMAL_COST && cost > 0) {
                    EdgeCost edgeCost = new EdgeCost().setEdgeNum(lastLockedEdge.cardNum).setAgvNum(car.getAGVNum()).setCost(cost)
                            .setStartNodeNum(0).setDestinationNodeNum(car.getStopCardNum())
                            .setTargetCity(car.getDestination() == null ? "null" : car.getDestination());
                    cacheExecutor.batchInsert(edgeCost);
                }

                try {
                    unlockEdge(cardNum);
                    if (graph.getEntranceMap().containsKey(this.lockedEdge.cardNum)) {
                        StringBuilder logMessage = new StringBuilder();
                        logMessage.append(car.getAGVNum()).append("AGV receive cardNum:").append(cardNum);
                        unlockNode(logMessage);
                        log.info(logMessage.toString());
                    }
                } catch (Exception e) {
                    log.error("exception: "+car.getAGVNum()+"  cardNum : " + cardNum, e);
                }
            }
            lockedEdgeStartTime = System.currentTimeMillis();
        } else if (NodeFunction.Parking.equals(graph.getNodeMap().get(cardNum).getFunction())) {
            if (!isStart) {
                this.lastLockedEdge = this.lockedEdge;
                this.lastLockedNode = this.lockedNode;
            }
            StringBuilder logMessage = new StringBuilder();
            logMessage.append(car.getAGVNum()).append("AGV receive cardNum:").append(cardNum);
            unlockNode(logMessage);
            tryLockNodeEdge(cardNum, logMessage);
        } else if (cardNum != routeNodeList.get(2)) {
            log.error("不应该出现的情况! AGVNum:" + this.car.getAGVNum() + " cardNum:" + cardNum
                    + " lastLockedEdge:" + lastLockedEdge
                    + " lastLockedNode:" + lastLockedNode);
        }
    }

    private void tryLockEdgeWithoutRoute(int cardNum){
        if (NodeFunction.Parking.equals(graph.getNodeMap().get(cardNum).getFunction())) {
            if (lastLockedEdge != null) {
                unlockEdge(lockedEdge.cardNum);
            }
            lockedEdge = graph.getEdgeMap().get(cardNum);
            lockedEdge.setLocked();
            lockedEdge.waitQueue.add(car);
            lastLockedEdge = lockedEdge;
            car.setExecutiveCommand(Command.STOP);
            log.info("命令" + car.getAGVNum() + "AGV停下来");
        } else if (lastLockedEdge != null){
            unlockEdge(lockedEdge.cardNum);
        }
    }

    private void tryLockNodeEdge(int cardNum, StringBuilder logMessage) {
        Node nextNode = null;
        Edge nextEdge = null;
        if (this.routeNodeList.size() > 2) {
            nextNode = graph.getNodeMap().get(this.routeNodeList.getFirst());
            nextEdge = Common.calculateEdge(nextNode.cardNum, graph.getNodeMap().get(this.routeNodeList.get(1)).cardNum, graph);
        } else if (this.routeNodeList.size() == 2) {
            nextNode = graph.getNodeMap().get(this.routeNodeList.getFirst());
            nextEdge = graph.getEdgeMap().get(this.routeNodeList.getLast());
        }

        if (nextEdge == null || nextNode == null) {
            logMessage.append(">>>到达终点");
            log.info(logMessage.toString());
            car.setExecutiveCommand(Command.STOP);
            return;
        }

        this.lockedEdge = nextEdge;
        this.lockedNode = nextNode;
        synchronized (this.lockedNode.cardNum) {
            synchronized (this.lockedEdge.cardNum) {
                if (this.lockedEdge.isLocked()) {
                    car.setExecutiveCommand(Command.STOP);
                    this.lockedEdge.waitQueue.offer(car);
                    logMessage.append("等待通过，因为").append(lockedEdge.cardNum).append("边被").append(lockedEdge.waitQueue.peek().getAGVNum()).append("AGV占用");
                    log.info(logMessage.toString());

                    List<Integer> lockLoop = getLockLoop(cardNum);
                    if (lockLoop != null && lockLoop.size() > 1 && lockLoop.contains(cardNum)) {
                        StringBuilder builder = new StringBuilder();
                        for (Integer lockEdgeNum : lockLoop) {
                            builder.append(lockEdgeNum).append("/");
                        }
                        log.error(car.getAGVNum() + "AGV deadlock!!!deadlock!!!deadlock!!! " + builder.toString());

                        if (!resolveDeadLock(lockLoop)) {
                            log.error(car.getAGVNum() + "AGV 无路可走！无路可走！无路可走！");
                            this.resolveDeadLock.putLockLoop(lockLoop);
                        }
                    }
                    return;
                } else if (this.lockedNode.isLocked()) {
                    if (graph.getEntranceMap().containsKey(this.lockedEdge.cardNum)) {
                        this.lockedNode.waitQueue.offer(car);
                        logMessage.append("等待去往分拣边，因为").append(lockedNode.getCardNum()).append("点被").append(lockedNode.waitQueue.peek().getAGVNum()).append("AGV占用");
                        log.info(logMessage.toString());
                        car.setExecutiveCommand(Command.STOP);
                        return;
                    } else {
                        this.lockedEdge.waitQueue.offer(car);
                        this.lockedEdge.setLocked();
                        this.lockedNode.waitQueue.offer(car);
                        logMessage.append("占用").append(lockedEdge.cardNum).append("边,等待通过，因为").append(lockedNode.getCardNum()).append("点被").append(lockedNode.waitQueue.peek().getAGVNum()).append("AGV占用");
                        log.info(logMessage.toString());
                        car.setExecutiveCommand(Command.STOP);
                        return;
                    }
                } else {
                    if (graph.getEntranceMap().containsKey(this.lockedEdge.cardNum)) {
                        this.lockedNode.waitQueue.offer(car);
                        this.lockedNode.setLocked();
                        car.setExecutiveCommand(Command.FORWARD);
                        logMessage.append("占用").append(this.lockedEdge.cardNum).append("点， 去往分拣边");
                    } else {
                        this.lockedEdge.waitQueue.offer(car);
                        this.lockedEdge.setLocked();
                        this.lockedNode.waitQueue.offer(car);
                        this.lockedNode.setLocked();
                        car.setExecutiveCommand(Command.FORWARD);
                        logMessage.append("占用").append(this.lockedEdge.cardNum).append("边和").append(this.lockedNode.cardNum).append("点，并通过");
                    }
                }
            }
        }
        log.info(logMessage.toString());
    }

    private void unlockEdge(int cardNum) {
        if (graph.getEntranceMap().containsKey(this.lastLockedEdge.cardNum)) {
            return;
        }
        synchronized (this.lastLockedEdge.cardNum) {
            Car myself = this.lastLockedEdge.waitQueue.poll();
            if (myself.getAGVNum() != car.getAGVNum()) {
                log.error(" 不应该出现的情况！ " + car.getAGVNum() + "AGV通过交汇点准备解除lastLockedEdge,但是该AGV不在waitQueue顶端");
            }

            if (!this.lastLockedEdge.waitQueue.isEmpty()) {
                Car carTmp = this.lastLockedEdge.waitQueue.peek();
                synchronized (carTmp.getTrafficControl().getLockedNode().cardNum) {
                    if (!carTmp.getTrafficControl().getLockedNode().isLocked()) {
                        carTmp.getTrafficControl().getLockedNode().setLocked();
                        carTmp.setExecutiveCommand(Command.FORWARD);
                        log.info("{}AGV receive cardNum:{} >>> 解除占用{}边 >>> {}AGV前进占用{}边",
                                car.getAGVNum(), cardNum, this.lastLockedEdge.cardNum, carTmp.getAGVNum(), lastLockedEdge.cardNum);
                    } else {
                        log.info("{}AGV receive cardNum:{} >>> 解除占用{}边 >>> {}AGV继续等待{}边，因为{}点被{}AGV占用！",
                                car.getAGVNum(), cardNum, lastLockedEdge.cardNum, carTmp.getAGVNum(), lastLockedEdge.cardNum,
                                carTmp.getTrafficControl().getLockedNode().cardNum,
                                carTmp.getTrafficControl().getLockedNode().waitQueue.peek().getAGVNum());
                    }
                    carTmp.getTrafficControl().getLockedNode().waitQueue.offer(carTmp);
                }
            } else {
                log.info("{}AGV receive cardNum:{} >>> {}边完全被解除占用", car.getAGVNum(), cardNum, lastLockedEdge.cardNum);
                this.lastLockedEdge.unlock();
            }
            this.lastLockedEdge = this.lockedEdge;
            this.lastLockedNode = this.lockedNode;
        }
    }

    private void unlockNode(StringBuilder logMessage) {
        if (this.lockedNode != null && this.lockedNode.isLocked()) {
            synchronized (this.lockedNode.cardNum) {
                Car myself = this.lockedNode.waitQueue.poll();
                if (myself.getAGVNum() != car.getAGVNum()) {
                    log.error("不应该出现的情况！ {}AGV通过停车点准备解除{}lockedNode,但是该AGV不在waitQueue顶端", car.getAGVNum(), this.lockedNode.cardNum);
                }
                if (!this.lockedNode.waitQueue.isEmpty()) {
                    Car carTmp = this.lockedNode.waitQueue.peek();
                    Edge carTmpLockedEdge = carTmp.getTrafficControl().getLastLockedEdge();
                    if ((carTmpLockedEdge.isLocked() && carTmpLockedEdge.waitQueue.peek().getAGVNum() == carTmp.getAGVNum()) ||
                            (graph.getEntranceMap().containsKey(carTmpLockedEdge.cardNum))) {
                        carTmp.setExecutiveCommand(Command.FORWARD);
                        logMessage.append(" >>> 解除占用").append(this.lockedNode.cardNum).append("点 >>> ").append(carTmp.getAGVNum()).append("AGV前进占用 >>>");
                    } else {
                        log.error("不应该出现的情况！ {}AGV没有锁住{}边，就想通过！", carTmp.getAGVNum(), carTmp.getTrafficControl().getLastLockedEdge().cardNum);
                    }
                } else {
                    logMessage.append(" >>> ").append(this.lockedNode.cardNum).append("点完全被解除占用 >>>");
                    this.lockedNode.unlock();
                }
                this.lockedNode = null;
            }
        }
    }

    public void setRouteNodeList(List<Integer> routeNodeList) {
        if (this.routeNodeList == null) {
            this.routeNodeList = new LinkedList<>();
        }
        this.routeNodeList.clear();
        this.routeNodeList.addAll(routeNodeList);
        this.routeNodeList.removeFirst();
        isStopToWait(car.getReadCardNum(), true);
    }

    private List<Integer> getLockLoop(int cardNum){
        List<Integer> lockLoop = new ArrayList<>(5);
        lockLoop.add(cardNum);
        lockLoop.add(lockedEdge.cardNum);
        Car carOnLockedEdge = this.lockedEdge.waitQueue.peek();
        Edge nextLockedEdge = carOnLockedEdge.getTrafficControl().getLockedEdge();

        if (nextLockedEdge != null) {
            synchronized (nextLockedEdge.cardNum) {
                if (nextLockedEdge.isLocked() && nextLockedEdge.waitQueue.peek().getAGVNum() != carOnLockedEdge.getAGVNum()) {
                    lockLoop.add(nextLockedEdge.cardNum);
                    Car carOnNextLockedEdge = nextLockedEdge.waitQueue.peek();
                    Edge nextNextLockedEdge = carOnNextLockedEdge.getTrafficControl().getLockedEdge();

                    if (nextNextLockedEdge != null) {
                        synchronized (nextNextLockedEdge.cardNum) {
                            if (nextNextLockedEdge.isLocked() && nextNextLockedEdge.waitQueue.peek().getAGVNum() != carOnNextLockedEdge.getAGVNum()) {
                                lockLoop.add(nextNextLockedEdge.cardNum);
                                Car carOnNextNextLockedEdge = nextNextLockedEdge.waitQueue.peek();
                                Edge next3LockedEdge = carOnNextNextLockedEdge.getTrafficControl().getLockedEdge();

                                if (next3LockedEdge != null) {
                                    synchronized (next3LockedEdge.cardNum) {
                                        if (next3LockedEdge.isLocked() && next3LockedEdge.waitQueue.peek().getAGVNum() != carOnNextNextLockedEdge.getAGVNum()) {
                                            lockLoop.add(nextNextLockedEdge.cardNum);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < lockLoop.size(); i++) {
            for (int j = i + 1; j <lockLoop.size(); j++) {
                if (lockLoop.get(i).equals(lockLoop.get(j))) {
                    return lockLoop.subList(i, j);
                }
            }
        }
        return null;
    }

    private boolean resolveDeadLock(List<Integer> lockLoop){
        for (Integer lockEdgeNum : lockLoop) {
            boolean result = resolveDeadLock.resolveDeadLock(lockEdgeNum);
            if (result) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Integer> getRouteNodeList() {
        return this.routeNodeList;
    }

    public Node getLockedNode() {
        return this.lockedNode;
    }

    public void setLockedNode(Node node){
        this.lockedNode = node;
    }

    public Edge getLastLockedEdge() {
        return this.lastLockedEdge;
    }

    public Edge getLockedEdge() {
        return this.lockedEdge;
    }

    @Override
    public void setCar(Car car) {
        this.car = car;
    }
}
