**SPINE** (SParsification of Influence NEtworks) is an algorithm that discovers the "backbone" of an influence network. Given a social network and a log of past propagations, we build an instance of the independent-cascade model that describes the propagations. We aim at reducing the complexity of that model, by preserving the network edges that are most likely to lead to propagations.

## Authors

[Antti Ukkonen](http://www.anttiukkonen.com/), 
[Aris Gionis](http://users.ics.aalto.fi/gionis/), 
[Carlos Castillo](http://chato.cl/research/), 
[Francesco Bonchi](http://www.francescobonchi.com/), 
[Michael Mathioudakis](http://www.mmathioudakis.com/)

## Publication

**Sparsification of influence networks**. In Proceedings of the 17th ACM SIGKDD international conference on Knowledge discovery and data mining (KDD '11). ACM, New York, NY, USA, 529-537.
[paper: [PDF](https://bitbucket.org/mmathioudakis/spine/downloads/spine.pdf), [ACM](http://doi.acm.org/10.1145/2020408.2020492), slides: [PDF](https://bitbucket.org/mmathioudakis/spine/downloads/spine-slides.pdf)]

## License

Please check the *LICENSE.txt* file under the [Downloads tab](https://bitbucket.org/mmathioudakis/spine/downloads).

## Instructions

### Data Format

Please check the [Downloads tab](https://bitbucket.org/mmathioudakis/spine/downloads) for sample data files  extracted from the [MemeTracker](http://memetracker.org/data.html) dataset. Files with extension ".sn" define a social network structure. Each line corresponds to one directed arc between two nodes. For example, the line
```
NodeA    NodeB 
```
signifies that there is a directed arc from 'NodeA' to 'NodeB'. This, in turn, means that 'NodeA' influences 'NodeB', with 'influence' defined in terms of the Independent Cascade Model (ICM - see [paper](https://bitbucket.org/mmathioudakis/spine/downloads/spine.pdf)).

Files with extension ".out" contain a set of propagations. Each propagation, in turn, is defined as a sequence of activations. A propagation begins with the activation of node 'omega', a special node that models sources of influence outside the network. In the data files, activations of 'omega' are represented with the following line,
```
    omega   0
```
which signifies that node 'omega' is activated at time 0. Every such line signifies the beginning of another propagation. Lines between 'omega' activations correspond to a single propagation, and each of them corresponds to the activation of one node. For example, line
```
NodeA    NodeB   T 
```
signifies that 'NodeA' successfully influenced 'NodeB' to perform an action at time T. In our paper, we assume that 'NodeA' is generally not known. Therefore, for the current implementation of Spine, 'NodeA' acts simply as a placeholder. This format was chosen in interest of consistency with possible extensions of 'Spine' that assume that this information is available. In the provided data files, 'NodeA' is uniformly set to 'omega'.
 
### Using SPINE

Please make sure you update your classpath with the jar files in the 'javalib' directory. If you don't compile the source files on your own, you should also include 'spine.0.1.jar' in the classpath.	 
 
#### Estimating Influence Probabilities
 
To estimate the influence probabilities given a social network and a set of propagations, execute the following command. Estimation uses the EM algorithm of Saito et.al., under the assuptions detailed in our paper.
```
java edu.toronto.cs.propagation.ic.ICEstimate -m 50 -d 1e-8 -s data.sn -i data.out -o data.probs
```
The parameters of this java command are explained below.
	 
* 	 -m 50: Maximum number of iterations of the EM algorithm.
* 	 -d 1e-8: Convergence criterion - L2 distance between the estimated probabilities of successive iterations.
* 	 -s data.sn: Input file that contains a social network (e.g. '-s memeS.sn')
* 	 -i data.out: Input file that contains a set of propagations (e.g. '-i memeS.out')
* 	 -o data.probs: Output file that contains the influence probabilities for every arc in the social network (e.g. '-o memeS.probs').

Each line in the output file corresponds to one arc, according to the following format,
```
NodeA    NodeB   p
```
which means that 'NodeA' influences 'NodeB' with probability p, according to the IC model.
 
#### Sparsification
 
To sparsify a social network using Spine, execute the following command.
```
java edu.toronto.cs.propagation.sparse.Sparsifier -s data.sn -i data.out -p data.probs -k K -o data.K.probs
```
The parameters of this java command are explained below.
	 
* 	 -s data.sn: Input file that contains a social network (e.g. '-s memeS.sn').
* 	 -i data.out: Input file that contains a set of propagations (e.g. '-i memeS.out').
* 	 -p data.probs: Input file that contains the influence probabilities for every arc in the social network (e.g. '-p memeS.probs'). It is the output of the estimation process described above.
* 	 -k K: Input parameter that specifies how many arcs to keep (e.g. '-k 150').
* 	 -o data.K.probs: Output file that contains only the K selected arcs (e.g. '-o memeS.150.probs').
 
To examine how the log-likelihood of the propagations varies for different sizes K when sparsification is performed by Spine, execute the following command.
```
java edu.toronto.cs.propagation.sparse.Sparsifier -s data.sn -i data.out -p data.probs -z data.spine
```

Explanation for the last parameter:

* 	 -z data.spine: Specifies an output file 'data.spine.logL' to store the results. The output file contains the log-likelihood of the propagations (specified in '-i data.out') for different sizes of a sparse network.
 
 
Similarly, one can examine the behavior of log-likelihood when sparsification is performed either randomly or by influence probability value (two 'naive' methods examined in our paper). The respective command is as follows.
```
java edu.toronto.cs.propagation.sparse.Sparsifier -s data.sn -i data.out -p data.probs -z data.naive -f NaiveMethod
```

Explanation for the last parameter:

* 	 -f NaiveMethod: Specify either as '-f NaiveByRandomSparsifier' or '-f NaiveByProbabilitySparsifier' to select arcs either randomly or by influence probability value, respectively.
