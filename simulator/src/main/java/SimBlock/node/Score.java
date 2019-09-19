package SimBlock.node;

import java.util.HashMap;
import java.util.Map;

public class Score{
	private Map<Node,Double> scores = new HashMap<Node,Double>();
	private double score = 0;
	private double average_score = 0;
	private double para = 0.3;

	public 	Map<Node,Double> getScores(){return scores;}
	public double getScore(Node node){return scores.get(node);}
	public double getAverageScore(){
		int i = 1;
		for(double val : scores.values()){
			average_score = average_score + val;
			i++;
		}
		return average_score/i;
	}

	public void addScore(Node from, long t_inv, long t_block){
		if(scores.get(from) == null){
			score = (t_inv-t_block);
			scores.put(from, score);
		}
		else{
			score = scores.get(from);
			scores.remove(from);
			scores.put(from, (1-para)*(score)+para*(t_inv-t_block));
		}
	}

	// public long getAverageScore(){
	// 	for(Node score; scores)
	// }
}