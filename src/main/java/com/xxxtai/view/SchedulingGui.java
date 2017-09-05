package com.xxxtai.view;

import com.xxxtai.controller.Algorithm;
import com.xxxtai.controller.SchedulingAGVRunnable;
import com.xxxtai.main.Main;
import com.xxxtai.model.*;
import com.xxxtai.myToolKit.AbsoluteToRelativeCoordinates;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SchedulingGui extends JPanel implements Gui{
	private static final long serialVersionUID = 1L;
	private RoundButton schedulingGuiBtn;
	private RoundButton settingGuiBtn;
	private RoundButton drawingGuiBtn;
	@Resource
	private Graph graph;
	@Resource
	private DrawingGraph drawingGraph;
	@Resource(name = "dijkstra")
	private Algorithm algorithm;
	@Resource
	private Runnable monitorServerSocketRunnable;
	@Resource
	private SchedulingAGVRunnable schedulingAGVRunnable;
	public static final ArrayList<Car> AGVArray =new ArrayList<>();
	private Timer timer;
	private ExecutorService executors;
	

	public SchedulingGui(){
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		schedulingGuiBtn = new RoundButton("调度界面");
		schedulingGuiBtn.setBounds(0, 0, screenSize.width/3, screenSize.height/20);
		schedulingGuiBtn.setForeground(new Color(30, 144, 255));
		schedulingGuiBtn.setBackground(Color.WHITE);
		
		settingGuiBtn = new RoundButton("设置界面");
		settingGuiBtn.setBounds(screenSize.width/3, 0, screenSize.width/3, screenSize.height/20);

		drawingGuiBtn = new RoundButton("制图界面");
		drawingGuiBtn.setBounds(2*screenSize.width/3, 0, screenSize.width/3, screenSize.height/20);

		JLabel stateLabel = new JLabel();
		stateLabel.setBounds(0, 22*screenSize.height/25, screenSize.width, screenSize.height/25);
		stateLabel.setFont(new Font("宋体", Font.BOLD, 25));

		this.setLayout(null);
		this.add(schedulingGuiBtn);
		this.add(settingGuiBtn);
		this.add(drawingGuiBtn);
		this.add(stateLabel);
		
		this.addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent e){
				if(e.getButton() == MouseEvent.BUTTON1){
					int x = e.getX();
					int y = e.getY();
					for(Node node : graph.getNodeArray()){
						if(Math.abs(x - node.X) < 40 && Math.abs(y - node.Y) < 40){
							Path path = algorithm.findRoute(AGVArray.get(0).getAtEdge(), node.CARD_NUM, true);
							if(path != null ){
								System.out.println();
								System.out.print(AGVArray.get(0).getAGVNum() + "AGVRoute:");
								for(Integer n : path.getRoute()){
									System.out.print(n + "/");
								}
								System.out.print("--relative：");
								String routeString = AbsoluteToRelativeCoordinates.convert(graph, path);
								System.out.println(routeString);
								AGVArray.get(0).sendMessageToAGV(routeString);
								AGVArray.get(0).setRouteNodeNumArray(path.getRoute());
							}else{
								System.out.println("无路可走");
							}
						}
					}
				}else if(e.getButton() == MouseEvent.BUTTON2){
					int x = e.getX();
					int y = e.getY();
					for(Node node : graph.getNodeArray()){
						if(Math.abs(x - node.X) < 40 && Math.abs(y - node.Y) < 40){
							Path path1 = algorithm.findRoute(AGVArray.get(1).getAtEdge(), node.CARD_NUM, false);
							if(path1 != null){
								System.out.println();
								System.out.print(AGVArray.get(1).getAGVNum() + "AGVroute:");
								for(Integer n : path1.getRoute()){
									System.out.print(n + "/");
								}
								System.out.print("--relative：");
								String routeString1 = AbsoluteToRelativeCoordinates.convert(graph, path1);
								System.out.println(routeString1);
								AGVArray.get(1).sendMessageToAGV(routeString1);
								AGVArray.get(1).setRouteNodeNumArray(path1.getRoute());
							}else{
								System.out.println("无路可走");
							}
						}
					}				
				}else if(e.getButton() == MouseEvent.BUTTON3){
					int x = e.getX();
					int y = e.getY();
					for(Node node : graph.getNodeArray()){
						if(Math.abs(x - node.X) < 40 && Math.abs(y - node.Y) < 40){
							Path path1 = algorithm.findRoute(AGVArray.get(2).getAtEdge(), node.CARD_NUM, false);
							if(path1 != null){
								System.out.println();
								System.out.print(AGVArray.get(2).getAGVNum() + "AGVroute:");
								for(Integer n : path1.getRoute()){
									System.out.print(n + "/");
								}
								System.out.print("---relative：");
								String routeString1 = AbsoluteToRelativeCoordinates.convert(graph, path1);
								System.out.println(routeString1);
								AGVArray.get(2).sendMessageToAGV(routeString1);
								AGVArray.get(2).setRouteNodeNumArray(path1.getRoute());
							}else {
								System.out.println("无路可走");
							}

						}
					}				
				}
				
			}
		});
		
		//AGVArray = new ArrayList<AGVArray>();
		

		timer = new Timer(50, new RepaintTimerListener());
		executors = Executors.newFixedThreadPool(2);
	}
	@PostConstruct
	public void init(){		
		for(int i = 0; i < 10; i++){
			Car car = getCar();
			car.init(i+1);
			AGVArray.add(car);
		}
		
		timer.start();
		executors.execute(monitorServerSocketRunnable);
		schedulingAGVRunnable.setAGVArray(AGVArray);
		executors.execute(schedulingAGVRunnable);
	}
	@Override
	public void paint(Graphics g){
		super.paint(g);
		drawingGraph.drawingMap(g);
		drawingGraph.drawingAGV(g, AGVArray,this);	
	}
	
	class RepaintTimerListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			repaint();
			for(Car car : AGVArray){
				car.stepByStep();
				car.heartBeat();
			}
		}
	}
	 
	
	public void getGuiInstance(Main main, SchedulingGui schedulingGui, SettingGui settingGui, DrawingGui drawingGui){
		schedulingGuiBtn.addActionListener(e -> {
            StringBuilder strBuf = new StringBuilder();
            strBuf.append("/*************************************************\n");
            strBuf.append("边 被 占 用 信 息 ：\n");
            for(Edge edge :graph.getEdgeArray()){
                if(!edge.waitQueue.isEmpty()){
                    strBuf.append(edge.CARD_NUM).append("边被");
                    for(Car car : edge.waitQueue)
                        strBuf.append(car.getAGVNum()).append(",");
                    strBuf.append("AGV占用！！");
                    if(!edge.isLocked())
                        strBuf.append(edge.CARD_NUM).append("边的waitQueue不为空，但未被锁住");
                    strBuf.append("\n");
                }else{
                    if(edge.isLocked()){
                        strBuf.append(edge.CARD_NUM).append("边被锁住，但waitQueue为空");
                        strBuf.append("\n");
                    }
                }

            }
            strBuf.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
            strBuf.append("点  被 占 用 信 息 ：\n");
            for(Node node :graph.getNodeArray()){
                if(!node.waitQueue.isEmpty()){
                    strBuf.append(node.CARD_NUM).append("点被");
                    for(Car car : node.waitQueue)
                        strBuf.append(car.getAGVNum()).append(",");
                    strBuf.append("AGV占用！！");
                    if(!node.isLocked())
                        strBuf.append(node.CARD_NUM).append("边的waitQueue不为空，但未被锁住");
                    strBuf.append("\n");
                }else{
                    if(node.isLocked()){
                        strBuf.append(node.CARD_NUM).append("边的waitQueue为空，但被锁住");
                        strBuf.append("\n");
                    }
                }
            }
            strBuf.append("*************************************************/\n");
            System.out.println(strBuf.toString());
        });
		settingGuiBtn.addActionListener(e -> {
            main.getContentPane().removeAll();
            main.getContentPane().add(settingGui);
            main.repaint();
            main.validate();
        });
		drawingGuiBtn.addActionListener(e -> {
            main.getContentPane().removeAll();
            main.getContentPane().add(drawingGui);
            main.repaint();
            main.validate();
        });
	}
	
	public ArrayList<Car> getAGVArray(){
		return SchedulingGui.AGVArray;
	}
	
	public abstract Car getCar();
}
