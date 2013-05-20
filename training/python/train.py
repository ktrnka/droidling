#!/usr/bin/env python
"""
Train the machine learning algorithms on the specified data set.
"""

import sys
import os
import io
from optparse import OptionParser
import csv
import pprint
from sklearn import svm, cross_validation, tree, neighbors, linear_model, datasets
from sklearn.decomposition import PCA
from sklearn.cross_validation import StratifiedKFold
import numpy
import StringIO
from collections import namedtuple
import pylab as pl
from collections import Counter

DataSet = namedtuple('DataSet', ['data', 'row_labels', 'targets', 'feature_names', 'target_names', 'regression_targets'])

def load(csvFile, classificationTargetColumn, regressionTargetColumn, derivedFeatures=True):
	labelColumn = 0
	data = []
	classificationTargets = []
	regressionTargets = []
	labels = []
	headers = []

	pp = pprint.PrettyPrinter(indent=4)
	targetSet = set(['y', 'n'])
	filterColumns = set([labelColumn, classificationTargetColumn, regressionTargetColumn])

	with io.open(csvFile, 'rb') as csvIn:
		reader = csv.reader(csvIn)

		headerLine = next(reader)
		headers = [headerLine[i] for i in xrange(len(headerLine)) if i not in filterColumns]

		if derivedFeatures:
		   headers.append("numWords")
		   headers.append("length")

		for row in reader:
			featureValues = [row[i] for i in xrange(len(row)) if i not in filterColumns]

			if derivedFeatures:
			   featureValues.append(len(row[labelColumn].split()))
			   featureValues.append(len(row[labelColumn]))

			classificationTarget = row[classificationTargetColumn]
			regressionTarget = row[regressionTargetColumn]

			if classificationTarget not in targetSet:
				continue

			classificationTargets.append(classificationTarget)
			regressionTargets.append(regressionTarget)
			data.append(featureValues)
			labels.append(row[labelColumn])

	#pp.pprint(headers)
	#pp.pprint(targets)
	#pp.pprint(data)

	dataset = DataSet(data=numpy.asarray(data), targets=numpy.fromiter([ 1 if x == 'y' else 0 for x in classificationTargets], numpy.int), target_names=['bad', 'good'], feature_names=headers, regression_targets=numpy.fromiter(regressionTargets, numpy.float), row_labels=labels)

	return dataset

def classifierExperiments(dataset):
	svc = svm.SVC(kernel='linear')

	scores = cross_validation.cross_val_score(svc, dataset.data, dataset.targets, cv=10)

	print "Linear kernel"
	for score in scores:
		print "Score: {0}".format(score)

	print "Mean {0:.2f} +/- {1:.2f}".format(scores.mean(), scores.std()/2)

	svc = svm.SVC(kernel='rbf')

	scores = cross_validation.cross_val_score(svc, dataset.data, dataset.targets, cv=10)

##	print "\nRBF kernel"
##	for score in scores:
##		print "Score: {0}".format(score)
##
##	print "Mean {0:.2f} +/- {1:.2f}".format(scores.mean(), scores.std()/2)

	print "\nDecision trees!"
	clf = tree.DecisionTreeClassifier()
	scores = cross_validation.cross_val_score(clf, dataset.data, dataset.targets, cv=10)

	for score in scores:
		print "Score: {0}".format(score)

	print "Mean {0:.2f} +/- {1:.2f}".format(scores.mean(), scores.std()/2)

##	print "\nKNN"
##	clf = neighbors.KNeighborsClassifier(3, weights="distance")
##	scores = cross_validation.cross_val_score(clf, dataset.data, dataset.targets, cv=10)
##
##	for score in scores:
##		print "Score: {0}".format(score)
##
##	print "Mean {0:.2f} +/- {1:.2f}".format(scores.mean(), scores.std()/2)

def pcaExperiments(dataset):
	pca = PCA(n_components=2, whiten=True)
	X_r = pca.fit_transform(dataset.data)

	counter = Counter([tuple(x) for x in X_r])
	print counter.most_common(10)

	pl.figure()
	for color, target_value, target_name in zip("rg", [0, 1], dataset.target_names):
		pl.scatter(X_r[dataset.targets == target_value, 0], X_r[dataset.targets == target_value, 1], c=color, label=target_name)
	pl.legend()
	pl.title('PCA of dataset')
	pl.show()

def generalGraph(dataset):
	"""Graph the aggregated value to see how it affects labeling"""
	pl.figure()
	for color, target_value, target_name in zip("rg", [0, 1], dataset.target_names):
		pl.scatter(dataset.data[dataset.targets == target_value, dataset.feature_names.index('Combined Score')], dataset.data[dataset.targets == target_value, dataset.feature_names.index('Frequency')], c=color, label=target_name)
	pl.legend()
	pl.title('Combined score and frequency')
	pl.show()

def datasetBalance(dataset):
	counts = Counter(dataset.targets)
	print counts.most_common()

def regressionExperiments(dataset, topN=5):
	"""Run regression tests against the data set."""

	simpleRegression = linear_model.LinearRegression()
	simpleRegression.fit(dataset.data[:-20], dataset.regression_targets[:-20])

	predictions = simpleRegression.predict(dataset.data[-20:])

	folds = StratifiedKFold(dataset.targets, n_folds=3)

	for i, (train, test) in enumerate(folds):
		print "Fold {0}\n\tTraining examples: {1}\n\tTesting examples: {2}".format(i, ", ".join([ str(j) for j in train ]), ", ".join([ str(j) for j in test ]))

		# train an algorithm
		regr = linear_model.LinearRegression()
		regr.fit(dataset.data[train,], dataset.regression_targets[train])

		for i in xrange(len(regr.coef_)):
			print "Coefficient weight for {0} [{1}]: {2}".format(dataset.feature_names[i], i, regr.coef_[i])

		# evaluate
		predicted = [ regr.predict(sample) for sample in dataset.data[test,] ]
		mse = numpy.mean((predicted - dataset.regression_targets[test]) ** 2)

		# sort examples by the predicted value
		pairedPredictions = [ (i, predicted[i]) for i in xrange(len(predicted)) ]
		sortedPredictions = sorted(pairedPredictions, key=lambda pair: pair[1], reverse=True)

		regressionScore = 0
		for pair in sortedPredictions[:topN]:
			regressionScore += dataset.regression_targets[pair[0]]

		# evaluate baseline
		baselinePaired = [ (i, dataset.data[i, -1]) for i in xrange(len(predicted)) ]
		baselineSorted = sorted(baselinePaired, key=lambda pair: pair[1], reverse=True)

		baselineScore = 0
		for pair in sortedPredictions[:topN]:
			baselineScore += dataset.regression_targets[pair[0]]

		print "Baseline sorting score: {0}".format(baselineScore)
		print "Regression sorting score: {1}".format(regressionScore)

def testRegression():
	"""The sklearn linear regression example to see if it works"""
	# Load the diabetes dataset
	diabetes = datasets.load_diabetes()


	# Use only one feature
	diabetes_X = diabetes.data[:, numpy.newaxis]
	diabetes_X_temp = diabetes_X[:, :, 2]

	# Split the data into training/testing sets
	diabetes_X_train = diabetes_X_temp[:-20]
	diabetes_X_test = diabetes_X_temp[-20:]

	# Split the targets into training/testing sets
	diabetes_y_train = diabetes.target[:-20]
	diabetes_y_test = diabetes.target[-20:]

	# Create linear regression object
	regr = linear_model.LinearRegression()

	# Train the model using the training sets
	regr.fit(diabetes_X_train, diabetes_y_train)

	# The coefficients
	print 'Coefficients: \n', regr.coef_
	# The mean square error
	print ("Residual sum of squares: %.2f" %
	       numpy.mean((regr.predict(diabetes_X_test) - diabetes_y_test) ** 2))
	# Explained variance score: 1 is perfect prediction
	print ('Variance score: %.2f' % regr.score(diabetes_X_test, diabetes_y_test))

	# Plot outputs
	pl.scatter(diabetes_X_test, diabetes_y_test,  color='black')
	pl.plot(diabetes_X_test, regr.predict(diabetes_X_test), color='blue',
	        linewidth=3)

	pl.xticks(())
	pl.yticks(())

	pl.show()

def main():
	parser = OptionParser()
	(options, args) = parser.parse_args()

	try:
		trainingFilename, = args
	except ValueError:
		#print "Not enough args\n" + __doc__
		trainingFilename = r'C:\Users\keith.trnka\Documents\GitHub\droidling\training\data\sms_phrase_log_updated.csv'
#		sys.exit(0)

	dataset = load(trainingFilename, 1, 2, derivedFeatures=True)

	#classifierExperiments(dataset)

	#datasetBalance(dataset)

	#generalGraph(dataset)
	#pcaExperiments(dataset)

	testRegression()
	regressionExperiments(dataset)


main()