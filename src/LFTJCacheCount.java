/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package src;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author s131061
 */
public class LFTJCacheCount extends LFTJ{
    private ArrayList<Cache> caches;
    private int counter[];
    private int totalCacheHits;
    private int v = 0;
    private ArrayList<Integer> owned = new ArrayList<>();
    private ArrayList<Integer> adhesion = new ArrayList<>();
    private TreeDecomposition td;
    private int cacheHitJumpCounter = 0;
    private int numberOfCacheResults = 0;
    private int numberOfComputedResults = 0;
    
    
    LFTJCacheCount(String dataSetPath, Enum CycleOrRoundsEnum, int amountOfPathOrCycle) throws IOException {
        super(dataSetPath, CycleOrRoundsEnum, amountOfPathOrCycle);
        // Executes the init method of LFTJ..
    }
    /**
     * Function which initializes the data sets and converts the data sets into iterators.
     * @param fileName The path from this sources root to the data set.
     * @param CycleOrRounds Specifies whether we are looking for # rounds or a complete cycle.
     * @param amountOfPathOrCycle Specifies the # of paths or cycles specified in the query, i.e. 3-path, 3-cycle etc.
     * When? At the start of the program.
     * Calls? When all iterators are still 'alive' we call leapfrogSearch.
     */
    @Override
    public void initDataSets(String fileName, Enum CycleOrRounds, int amountOfPathOrCycle) throws IOException{
        caches = new ArrayList<>();
        //create tree decomposition
        td = new TreeDecomposition(CycleOrRounds, amountOfPathOrCycle, debug>1);
        //create cache and counter for each bag and initialize to zero
        counter = new int[td.nrOfBags];
        for(int i = 0; i < td.nrOfBags; i++){
            caches.add(new Cache());
            counter[i] = 0;
        }
        totalCacheHits = 0;
        
        
        // For a cycle we have as many relations as amountOfPathOrCycle, i.e. a 4-cycle query gives 4 relations
        int amountOfRelations = amountOfPathOrCycle;
        // For a path we have one less, i.e. a 4-path query gives 3 relations
        if (CycleOrRounds == LFTJ.CycleOrRoundsEnum.PATH) {
            amountOfRelations--;
        }
        ArrayList<RelationIterator<Integer>> relIts = new ArrayList<>();
        int i;
        for(i = 1; i <= amountOfRelations; i++){
            DataImporter di;
            if(CycleOrRounds == LFTJ.CycleOrRoundsEnum.CYCLE && i == amountOfRelations) {
                di = new DataImporter(fileName, true, debug>1);
            } else {
                di = new DataImporter(fileName, false, debug>1);
            }
            TreeRelation rel = di.getRelArray();
            rel.setUid(i);
            RelationIterator<Integer> relIterator = rel.iterator();
            relIts.add(relIterator);
        }
        
        maxDepth = amountOfPathOrCycle - 1;
       
        iteratorPerDepth = new ArrayList<>();
        for (int j = 0; j <= maxDepth; j++) {
            ArrayList<RelationIterator<Integer>> intermedAListForIterators = new ArrayList<>();

            int a = Math.max(0, j - 1);
            int b = Math.min(j, maxDepth - 1);
            for (int k = a; k <= b; k++) {
                intermedAListForIterators.add(relIts.get(k));
                if(debug>0){ System.out.println("added at depth " + j + " iterator " + k); }
            }
            
            //for a cycle query, we add for the first and last depth, the last iterator (this creates the cycle)
            if((CycleOrRounds == LFTJ.CycleOrRoundsEnum.CYCLE) && (j == 0 || j == maxDepth)) {
                intermedAListForIterators.add(relIts.get(maxDepth));
                if(debug>0){ System.out.println("added at depth " + j + " iterator " + (maxDepth)); }
            }
            
            iteratorPerDepth.add(intermedAListForIterators);
            
        }
        
    }

    /**
     * Function which initializes the algorithm.
     * When? At the start after each time a key was found or we went a level higher.
     * Calls? When all iterators are still 'alive' we call leapfrogSearch.
     */
    @Override
    public void multiJoin(){
        // Start out by initializing/starting the algorithm with leapfrogOpen. This is the main loop.
        leapfrogOpen();
        
        while (true){ // This is our search function
            if(debug>=2) { printDebugInfo("A - Continue with the true loop"); }

            if(atEnd){ // If we did not find the specific value we were looking for and an iterator is at an end.
                if(debug>=2) { printDebugInfo("B2 - We got one iterator that is at an end"); }

                if(depth==0){ // We stop because we are all the way at the end
                    break;
                }
                // We continue the search. At this depth we were at the end, so we go one up and go to the next value.
                //Before we go up, check if depth is first in bag, if so store cache (lines 22,23 from algorithm in paper)

                // @TODO implement that it does not do this is the bag already exists..
                if(v != 0 && depth == td.owned.get(v).get(0)) {

                    VariableAssignment va = new VariableAssignment(adhesion.get(0), currentTuple.get(adhesion.get(0)));
                    va.counter = counter[v];
                    caches.get(v).addAssignment(va);

                    if(debug>=2){
                        System.out.println("At depth: " + depth + " currentTuple: " + currentTuple +
                                " checking for assignment " + adhesion.get(0) + " = " +(currentTuple.get(adhesion.get(0))));
                        System.out.println("Current bag: "+v+ " adhesion: " + adhesion +
                                " assignment: "+(currentTuple.get(adhesion.get(0))));
                        System.out.println(currentTuple);
                        System.out.println("Created assignment for key: "+va.assignment+", variable: "+va.variable +
                                " in depth: "+ depth);
                    }

                    // Create the values for the cache such that they come out in the results.
                    ArrayList<ArrayList<Integer>> tempTupleList = new ArrayList<>();
                    for(int i = result.size()-1; i >= 0; i--){
                        if(currentTuple.equals(result.get(i).subList(0,depth))){
                            ArrayList<Integer> tempTuple = new ArrayList<>();
                            tempTuple.addAll(result.get(i).subList(depth, result.get(i).size()));
                            tempTupleList.add(tempTuple);

                            if(debug>=2){ System.out.println("Added single item to cache: "+tempTuple);}
                        } else {
                            break;
                        }
                    }
                    for(int i = tempTupleList.size()-1; i>=0; i--){
                        caches.get(v).addOwnedKeyResults(va, tempTupleList.get(i));
                    }

                    if(debug>=2){
                        System.out.println();
                        System.out.println("Added to cache is: ");
                        System.out.println(caches.get(v).returnOwnedKeys(va.assignment));
                        System.out.println();
                        printDebugInfo("B3 - Executed leapfrogUp and leapfrogNext");
                    }
                }
                // Then go up..
                leapfrogUp();
                // And after we go up, check if depth is last in bag, if so update counter.
                // As specified in the caching paper on lines 18-20 from the algorithm
                if(v != 0 && depth == td.owned.get(v).get(td.owned.get(v).size()-1)) {
                    counter[v] = counter[v] + counter[v+1];
                }
                leapfrogNext();

            } else { // No iterator is at it's end.
                if(depth == maxDepth){// We got a winner
                    ArrayList<Integer> tuple = new ArrayList<>();
                    currentTuple.add(key);
                    tuple.addAll(currentTuple);
                    result.add(tuple);
                    numberOfComputedResults++;
                    currentTuple.remove(currentTuple.size()-1);

                    key = -1;
                    
                    //incrementer counter of bag
                    counter[v] = counter[v] + 1;

                    if(debug>=2) {
                        System.out.println("We found a match!");
                        System.out.println("");
                        System.out.println(result);
                        System.out.println("");
                    }
                    leapfrogNext();
                } else if (depth > maxDepth) {
                    // @TODO check if this one is really necessary?
                    // Can't seem to find Necessary in the output now..
                    for(int i = 0; i <= cacheHitJumpCounter; i++){
                        leapfrogUp();
                        System.out.println("went up to "+depth);
                        System.out.println("Necessary");
                    }
                    // @TODO end of todo

                    if(depth >= 0) { // Continue the search
                        leapfrogNext();
                    }
                } else {// We can still go level deeper.
                    if(debug>=2) {System.out.println("C2 - Depth -> Level down"); }
                    leapfrogOpen();
                }
            }
        }
        endTime = System.nanoTime();
        printResults();
    }
    
    /**
     * Function which initializes the algorithm.
     * When? At the start after each time a key was found or we went a level higher.
     * Calls? When all iterators are still 'alive' we call leapfrogsearch.
     */
    @Override
    public void leapfrogInit() {
        for(RelationIterator<Integer> relIt : iteratorPerDepth.get(depth)) {
            if(relIt.atEnd()) {
                atEnd = true;
                return;
            }
        }
        //initialize bag counter
        // @TODO Shouldn't we put this in the part where this is not at the end?
        // Seems like this could avoid initializing bags that shouldn't be initialized, right?
        if(v != 0 && depth == owned.get(0)) {
            counter[v] = 0;

            if(debug>=2){
                System.out.println("At depth: " + depth + " currentTuple: " + currentTuple +
                        " checking for assignment " + adhesion.get(0) + " = " +(currentTuple.get(adhesion.get(0))));
                System.out.println("Current bag: " + v + " adhesion: " + adhesion +
                        " assignment: " + (currentTuple.get(adhesion.get(0))));
            }
            
            //check for cache hit
            if(caches.get(v).containsAssignment(adhesion.get(0), currentTuple.get(adhesion.get(0)))) {

                if(debug>=1){ System.out.println("cache hit");}

                totalCacheHits++;
                // Adding the results to our arrayList result
                ArrayList<ArrayList<Integer>> allCacheResults;
                allCacheResults = caches.get(v).returnOwnedKeys(currentTuple.get(adhesion.get(0)));
                for (ArrayList<Integer> allCacheResult : allCacheResults) {
                    ArrayList<Integer> tempTuple = new ArrayList<>();
                    tempTuple.addAll(currentTuple);
                    tempTuple.addAll(allCacheResult);
                    result.add(tempTuple);
                    numberOfCacheResults++;
                }

                if(debug>=1){
                    System.out.println();
                    System.out.println(result);
                    System.out.println();
                }

                // Updating the counter
                counter[v] = caches.get(v).lastChecked.counter;
                int m = 0;
                for(int i = 0; i < td.nrOfVariables; i++) {
                    if (td.owner.get(i) >= v) {
                        m = i;
                    }
                }

                //@TODO It seems like this works, lisette do you agree?
                cacheHitJumpCounter = m - depth;

                leapfrogUp();
                leapfrogNext();
                //@TODO end of todo
                return;
            }

        }
        // If all iterators are still 'alive' we make sure everything is sorted and start searching for the first
        // possible match.
        if(!atEnd) {
            atEnd = false;
            Collections.sort(iteratorPerDepth.get(depth));
            p = 0;
            leapfrogSearch();
        }
    }

    /**
     * Function which searches for a match in the leapfrogtreejoin. Created as in the paper.
     */
    // @TODO - Verify that we can actually delete leapfrogSearch from this page as we don't need to modify it :)!
    /*@Override
    public void leapfrogSearch() {
        // maxKeyIndex is the index of the maximal element we found and maxKey is the actual value.
        // (Correction needed since -1 % 3 returns -1 and not 2 as we want)
        int maxKeyIndex = numIters-1;
        // I don't think we need the following line???
//        maxKeyIndex = numIters == 1 ? 0 : maxKeyIndex; // Special case where maxKeyIndex = 1 while numIters = 1
        int maxKey = iteratorPerDepth.get(depth).get(maxKeyIndex).key();

        while (true) {
            int minKey = iteratorPerDepth.get(depth).get(p).key();

            if(debug>=2) {
                System.out.println("maxKey: "+maxKey + ", maxKeyIndex: " + maxKeyIndex + ", numIters: " + numIters);
                System.out.println("minKey: "+minKey);
                System.out.println("-- Searching -- Depth: " + depth + ", MaxKeyIndex: " + maxKeyIndex + ", NumIters: "
                        + numIters + ", maxKey: " + maxKey + ", minKey: " + minKey);
                System.out.println("curIt values: " + iteratorPerDepth.get(depth).get(p).debugString());
            }

            if (maxKey == minKey) {
                if(debug>=2) {
                    System.out.println("Found key = "+minKey);
                    System.out.println("We currently have as result = "+currentTuple);
                }

                key = minKey;
                return;
            }
            else { // If no common key is found, update pointer of iterator
                if(debug>=2) {
                    System.out.println("Key not equal, Searching for " + maxKey + " with minkey " + minKey);
                    System.out.println("Seek with: "+iteratorPerDepth.get(depth).get(p).debugString());
                    System.out.println("Depth: "+ depth + " P: "+p);
                }

                iteratorPerDepth.get(depth).get(p).seek(maxKey);
                if(iteratorPerDepth.get(depth).get(p).atEnd()){ // The maxKey is not found
                    if(debug>=2) {System.out.println("key = -1");}
                    atEnd = true;
                    return;
                } else { // A new maxKey is found and thus we check if the next iterator can also find it
                    maxKey = iteratorPerDepth.get(depth).get(p).key();
                    p = (p + 1) % numIters;
                }
            }
        }
    }*/

    /**
     * Function which opens up the next level(goes one level down) for all iterators.
     * When? - This function is used when we are going to the next level.
     * Calls? - Calls leapfrogInit afterwards to start up the next search.
     * Calls? - Calls updateIterPandNumIters which makes sure our p does not go out of bound and numIters is updated.
     * Modifies - currenTuple. Adds the current key to currentTuple and then proceeds with the opening.
     */
    @Override
    public void leapfrogOpen(){
        if(depth > -1){ // Used to be able to report the currentTuple easily.
            currentTuple.add(iteratorPerDepth.get(depth).get(0).key());
        }
        depth = depth + 1;
        if(depth <= maxDepth) {
            if(debug>=2){System.out.println("depth <= maxDepth");}
            updateBag();
            updateIterPandNumIters();
            for(RelationIterator relIt : iteratorPerDepth.get(depth) ) {
                if(debug>=2){printDebugInfo("Extra iterator info for debug ");}
                relIt.open();
                if(debug>=2){printDebugInfo("Extra iterator info for debug ");}
            }
            leapfrogInit();
        }
    }

    /**
     * Function which goes up one level up for every iterator at this specific depth.
     * When? - This function is used when we are going to a lower depth.
     * Calls? - Calls leapfrogInit afterwards to start up the next search.
     * Calls? - Calls updateIterPandNumIters which makes sure our p does not go out of bound and numIters is updated.
     * Modifies - currentTuple. Removes the last added item which was the one from the depth just before we call up.
     */
    @Override
    public void leapfrogUp(){
        if(depth <= currentTuple.size() && depth > 0){
            currentTuple.remove(currentTuple.size()-1);
        }
        if(depth <= maxDepth){
            for(RelationIterator relIt : iteratorPerDepth.get(depth) ) {
                relIt.up();
            }
        }
        depth = depth - 1;
        if(depth >=0 && depth <= maxDepth) {
            updateBag();
            updateIterPandNumIters();
        }
        
    }

    // @TODO Documentation

    /**
     * bladiebla
     */
    private void updateBag() {
        v = td.owner.get(depth);
        owned = td.owned.get(v);
        adhesion = td.adhesion.get(v);
    }


    /**
     * Function to print the results in such a way with tabs that it can be reused.
     */
    private void printResults(){
        System.out.println("Caching" + "\t" +
                resultCycleOrPath + "\t" +
                resultAmountOfCycleorPath + "\t" +
                (midTime-startTime)/1000000 + "\t" +
                (endTime-midTime)/1000000 + "\t" +
                (endTime-startTime)/1000000 + "\t" +
                result.size() + "\t" +
                numberOfCacheResults + "\t" +
                numberOfComputedResults + "\t" +
                totalCacheHits + "\t"
        );
    }

    
    /**
     * @param args the command line arguments.
     */
    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();
        LFTJCacheCount lftjcc = new LFTJCacheCount("./data/CA-GrQc.txt", CycleOrRoundsEnum.PATH, 4); // Create a LFTJ with cache, load the datasets and ready to rumble
        long midTime = System.nanoTime();
        lftjcc.multiJoin(); // We start the joins and count the cache
        long endTime = System.nanoTime();
        printRunningTimes(startTime, midTime, endTime);
    }
    
}
