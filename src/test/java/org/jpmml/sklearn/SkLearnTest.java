/*
 * Copyright (c) 2015 Villu Ruusmann
 *
 * This file is part of JPMML-SkLearn
 *
 * JPMML-SkLearn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SkLearn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SkLearn.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.sklearn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.google.common.io.ByteStreams;
import h2o.estimators.BaseEstimator;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.EvaluatorBuilder;
import org.jpmml.evaluator.ModelEvaluatorBuilder;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.Batch;
import org.jpmml.evaluator.testing.IntegrationTest;
import org.jpmml.evaluator.testing.IntegrationTestBatch;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.jpmml.python.CompressedInputStreamStorage;
import org.jpmml.python.PickleUtil;
import org.jpmml.python.Storage;
import sklearn.Estimator;
import sklearn2pmml.pipeline.PMMLPipeline;

abstract
public class SkLearnTest extends IntegrationTest {

	public SkLearnTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	public boolean isGuarded(){
		return true;
	}

	@Override
	protected Batch createBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		Batch result = new IntegrationTestBatch(name, dataset, predicate, equivalence){

			@Override
			public IntegrationTest getIntegrationTest(){
				return SkLearnTest.this;
			}

			@Override
			public EvaluatorBuilder getEvaluatorBuilder() throws Exception {
				EvaluatorBuilder evaluatorBuilder = super.getEvaluatorBuilder();

				boolean guarded = isGuarded();
				if(!guarded){
					evaluatorBuilder = ((ModelEvaluatorBuilder)evaluatorBuilder)
						.setDerivedFieldGuard(null)
						.setFunctionGuard(null);
				}

				return evaluatorBuilder;
			}

			@Override
			public PMML getPMML() throws Exception {
				SkLearnEncoder encoder = new SkLearnEncoder();

				PMMLPipeline pipeline;

				try(Storage storage = openStorage("/pkl/" + getName() + getDataset() + ".pkl")){
					pipeline = (PMMLPipeline)PickleUtil.unpickle(storage);
				}

				Estimator estimator = pipeline.getFinalEstimator();

				File tmpFile = null;

				if(estimator instanceof BaseEstimator){
					BaseEstimator baseEstimator = (BaseEstimator)estimator;

					tmpFile = File.createTempFile(getName() + getDataset(), ".mojo.zip");

					String mojoPath = baseEstimator.getMojoPath();

					try(InputStream is = open("/" + mojoPath)){

						try(OutputStream os = new FileOutputStream(tmpFile)){
							ByteStreams.copy(is, os);
						}
					}

					baseEstimator.setMojoPath(tmpFile.getAbsolutePath());
				}

				PMML pmml = pipeline.encodePMML(encoder);

				validatePMML(pmml);

				if(tmpFile != null){
					tmpFile.delete();
				}

				return pmml;
			}

			@Override
			public List<Map<FieldName, String>> getInput() throws IOException {
				String dataset = super.getDataset();

				if(dataset.endsWith("Cat")){
					dataset = dataset.substring(0, dataset.length() - "Cat".length());
				} else

				if(dataset.endsWith("Dict")){
					dataset = dataset.substring(0, dataset.length() - "Dict".length());
				}

				return loadRecords("/csv/" + dataset + ".csv");
			}

			private Storage openStorage(String path) throws IOException {
				InputStream is = open(path);

				try {
					return new CompressedInputStreamStorage(is);
				} catch(IOException ioe){
					is.close();

					throw ioe;
				}
			}
		};

		return result;
	}
}