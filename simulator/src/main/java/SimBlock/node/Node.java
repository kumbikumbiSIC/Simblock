/**
 * Copyright 2019 Distributed Systems Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package SimBlock.node;

import static SimBlock.settings.SimulationConfiguration.*;
import static SimBlock.simulator.Main.*;
import static SimBlock.simulator.Network.*;
import static SimBlock.simulator.Simulator.*;
import static SimBlock.simulator.Timer.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import java.util.Random;	//add

import SimBlock.node.Score;		//add
import SimBlock.node.routingTable.AbstractRoutingTable;
import SimBlock.task.AbstractMessageTask;
import SimBlock.task.BlockMessageTask;
import SimBlock.task.InvMessageTask;
import SimBlock.task.MiningTask;
import SimBlock.task.RecMessageTask;
import SimBlock.task.Task;

public class Node {
	private int region;
	private int nodeID;
	private long miningRate;

	// private ArrayList<Double> score = new ArrayList<Double>();		//add
	// private static double average_score = 0;							//add		

	private Score score;		//add
	private int update_node_num = 3;		//add
	private Random rand = new Random();	//add

	private AbstractRoutingTable routingTable;

	private Block block;
	private Set<Block> orphans = new HashSet<Block>();

	private Task executingTask = null;

	private boolean sendingBlock = false;
	private ArrayList<RecMessageTask> messageQue = new ArrayList<RecMessageTask>();
	private Set<Block> downloadingBlocks = new HashSet<Block>();

	private long processingTime = 2;

	public Node(int nodeID,int nConnection ,int region, long power, String routingTableName){
		this.nodeID = nodeID;
		this.region = region;
		this.miningRate = power;

		score = new Score();		//add

		// this.score = 0;			//add
		// this.average_score = 0;	//add

		try {
			this.routingTable = (AbstractRoutingTable) Class.forName(routingTableName).getConstructor(Node.class).newInstance(this);
			this.setnConnection(nConnection);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getNodeID(){ return this.nodeID; }
	public Block getBlock(){ return this.block; }
	public long getPower(){ return this.miningRate; }
	public Set<Block> getOrphans(){ return this.orphans; }
	public void setRegion(int region){ this.region = region; }
	public int getRegion(){ return this.region; }

	// public double getScore(){ return this.score;}				//add	
	// public double getAverageScore(){ return average_score; }	//add

	//
	public boolean addNeighbor(Node node){ 
		//スコアが一定以上なら追加　をこれから実装 
		System.out.println("Node: addNeighbor");
		return this.routingTable.addNeighbor(node);  
	}
	public boolean removeNeighbor(Node node){ 
		// スコアが一定以下ならば削除　をこれから実装
		System.out.println("Node: removeNeighbor");
		return this.routingTable.removeNeighbor(node); 
	}
	public ArrayList<Node> getNeighbors(){ return this.routingTable.getNeighbors();}
	public AbstractRoutingTable getRoutingTable(){ return this.routingTable;}
	public void setnConnection(int nConnection){ this.routingTable.setnConnection(nConnection);}
	public int getnConnection(){ return this.routingTable.getnConnection(); }

	public void joinNetwork(){
		this.routingTable.initTable();
	}

	public void genesisBlock(){
		Block genesis = new Block(1, null, this, 0);
		this.receiveBlock(genesis);
	}

	public void addToChain(Block newBlock) {
		if(this.executingTask != null){
			removeTask(this.executingTask);
			this.executingTask = null;
		}
		this.block = newBlock;
		printAddBlock(newBlock);
		arriveBlock(newBlock, this);
	}

	private void printAddBlock(Block newBlock){
		OUT_JSON_FILE.print("{");
		OUT_JSON_FILE.print(	"\"kind\":\"add-block\",");
		OUT_JSON_FILE.print(	"\"content\":{");
		OUT_JSON_FILE.print(		"\"timestamp\":" + getCurrentTime() + ",");
		OUT_JSON_FILE.print(		"\"node-id\":" + this.getNodeID() + ",");
		OUT_JSON_FILE.print(		"\"block-id\":" + newBlock.getId());
		OUT_JSON_FILE.print(	"}");
		OUT_JSON_FILE.print("},");
		OUT_JSON_FILE.flush();
	}

	private void printAverageScore(Node node, double score){
		// AVERAGESCORE_JSON_FILE.print("{");
		AVERAGESCORE_CSV_FILE.print(node + "," + score + "\n");
		// AVERAGESCORE_JSON_FILE.print("}");
	}

	public void addOrphans(Block newBlock, Block correctBlock){
		if(newBlock != correctBlock){
			this.orphans.add(newBlock);
			this.orphans.remove(correctBlock);
			if(newBlock.getParent() != null && correctBlock.getParent() != null){
				this.addOrphans(newBlock.getParent(),correctBlock.getParent());
			}
		}
	}

	public void mining(){
		Task task = new MiningTask(this);
		this.executingTask = task;
		putTask(task);
	}

	public void sendInv(Block block){
		for(Node to : this.routingTable.getNeighbors()){
			AbstractMessageTask task = new InvMessageTask(this,to,block);

			// System.out.println("1");
			putTask(task);
		}
	}

	public void receiveBlock(Block receivedBlock){
		Block sameHeightBlock;

		if(this.block == null){
			this.addToChain(receivedBlock);
			this.mining();
			this.sendInv(receivedBlock);

		}else if(receivedBlock.getHeight() > this.block.getHeight()){
			sameHeightBlock = receivedBlock.getBlockWithHeight(this.block.getHeight());
			if(sameHeightBlock != this.block){
				this.addOrphans(this.block, sameHeightBlock);
			}
			this.addToChain(receivedBlock);
			this.mining();
			this.sendInv(receivedBlock);

		//orphan の処理
		}else if(receivedBlock.getHeight() <= this.block.getHeight()){
			sameHeightBlock = this.block.getBlockWithHeight(receivedBlock.getHeight());
			if(!this.orphans.contains(receivedBlock) && receivedBlock != sameHeightBlock){
				this.addOrphans(receivedBlock, sameHeightBlock);
				arriveBlock(receivedBlock, this);
			}
		}
	}

	public void receiveMessage(AbstractMessageTask message){
		Node from = message.getFrom();

		if(message instanceof InvMessageTask){
			Block block = ((InvMessageTask) message).getBlock();
			if(!this.orphans.contains(block) && !this.downloadingBlocks.contains(block)){
				if(this.block == null || block.getHeight() > this.block.getHeight()){
					AbstractMessageTask task = new RecMessageTask(this,from,block);
					putTask(task);
					downloadingBlocks.add(block);
				}else{

					// get orphan block
					if(block != this.block.getBlockWithHeight(block.getHeight())){
						AbstractMessageTask task = new RecMessageTask(this,from,block);
						putTask(task);
						downloadingBlocks.add(block);
					}
				}
			}
		}

		if(message instanceof RecMessageTask){
			this.messageQue.add((RecMessageTask) message);
			if(!sendingBlock){
				this.sendNextBlockMessage();
			}
		}

		if(message instanceof BlockMessageTask){
			Block block = ((BlockMessageTask) message).getBlock();
			downloadingBlocks.remove(block);
			this.receiveBlock(block);

			//送信元ノードのスコアを更新　add
			// if(this.nodeID == message.getTo().getNodeID()){System.out.println("in");}
			BlockMessageTask m = (BlockMessageTask) message;
			// score.addScore(message.getFrom(),m.getReceptionTimestamp(),block.getTime());

			// System.out.println("message.getFrom(): " + message.getFrom());
			// System.out.println("m.getReceptionTimestamp(): " + m.getReceptionTimestamp());
			// System.out.println("block.getTime(): " + block.getTime());
			if(block.getTime() != 0){
				score.addScore(message.getFrom(),m.getReceptionTimestamp(),block.getTime());	
			}
			// System.out.println("average_score :" + score.getAverageScore());
		
			//10の倍数なら隣接ノードを更新
			if(block.getId() % 10 == 0 && block.getId() != 0){

				// System.out.println(this + ": " + score.getAverageScore());
				printAverageScore(this,score.getAverageScore());

				for(int i=0; i < update_node_num; i++){

					getSimulatedNodes().size();
					// getSimulatedNodes().get(id);

					// System.out.println("in Node " + score.getScore(message.getFrom()));
					if(score.getScore(message.getFrom()) >= score.getAverageScore()){
						routingTable.removeNeighbor(message.getFrom());
						routingTable.addNeighbor(getSimulatedNodes().get(rand.nextInt(getSimulatedNodes().size())));
					}
					else{
					}
				}
				// removeNeighbor addNeighbor

			}
		}
	}

	// send a block to the sender of the next queued recMessage
	public void sendNextBlockMessage(){
		if(this.messageQue.size() > 0){

			sendingBlock = true;

			Node to = this.messageQue.get(0).getFrom();
			Block block = this.messageQue.get(0).getBlock();

			this.messageQue.remove(0);
			long blockSize = BLOCKSIZE;
			long bandwidth = getBandwidth(this.getRegion(),to.getRegion());
			long delay = blockSize * 8 / (bandwidth/1000) + processingTime;
			BlockMessageTask messageTask = new BlockMessageTask(this, to, block, delay);

			putTask(messageTask);
		}else{
			sendingBlock = false;
		}
	}

}
