/*******************************************************************************
 * Copyright (c) 2018 Fraunhofer IEM, Paderborn, Germany.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *  
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Johannes Spaeth - initial API and implementation
 *******************************************************************************/
package boomerang.preanalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JNopStmt;
import soot.jimple.internal.JimpleLocal;

public class PreTransformBodies extends BodyTransformer {

	private int replaceCounter;

	@Override
	protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
		addNopStmtToMethods(b);
		transformConstantAtFieldWrites();
	}

	private void transformConstantAtFieldWrites() {
		Map<Unit, Body> cwnc = getStmtsWithConstants();
		for (Unit u : cwnc.keySet()) {
			Body body = cwnc.get(u);
			if (u instanceof AssignStmt) {
				AssignStmt assignStmt = (AssignStmt) u;
				if (isFieldRef(assignStmt.getLeftOp()) && assignStmt.getRightOp() instanceof Constant) {
					String label = "varReplacer" + new Integer(replaceCounter++).toString();
					Local paramVal = new JimpleLocal(label, assignStmt.getRightOp().getType());
					AssignStmt newUnit = new JAssignStmt(paramVal, assignStmt.getRightOp());
					body.getLocals().add(paramVal);
					body.getUnits().insertBefore(newUnit, u);
					AssignStmt other = new JAssignStmt(assignStmt.getLeftOp(), paramVal);
					body.getUnits().insertBefore(other, u);
					body.getUnits().remove(u);
				}
			}
			if (u instanceof Stmt && ((Stmt)u).containsInvokeExpr()) {
				Stmt stmt = (Stmt) u;
				List<ValueBox> useBoxes = stmt.getInvokeExpr().getUseBoxes();
				for(Value v : stmt.getInvokeExpr().getArgs()) {
					if(v instanceof Constant) {
						String label = "varReplacer" + new Integer(replaceCounter++).toString();
						Local paramVal = new JimpleLocal(label, v.getType());
						AssignStmt newUnit = new JAssignStmt(paramVal, v);
						body.getLocals().add(paramVal);
						body.getUnits().insertBefore(newUnit, u);
						for(ValueBox b : useBoxes) {
							if(b.getValue().equals(v)) {
								b.setValue(paramVal);
							}
						}
					} else {
					}
				}
			}
		}
	}

	private void addNopStmtToMethods(Body b) {
		b.getUnits().addFirst(new JNopStmt());

	}

	private Map<Unit, Body> getStmtsWithConstants() {
		Map<Unit, Body> retMap = new LinkedHashMap<Unit, Body>();
		for (SootClass sc : Scene.v().getClasses()) {
			for (SootMethod sm : sc.getMethods()) {
				if (!sm.hasActiveBody())
					continue;
				Body methodBody = sm.retrieveActiveBody();
				for (Unit u : methodBody.getUnits()) {
					if (u instanceof AssignStmt) {
						AssignStmt assignStmt = (AssignStmt) u;
						if (isFieldRef(assignStmt.getLeftOp()) && assignStmt.getRightOp() instanceof Constant) {
							retMap.put(u, methodBody);
						}
					}
					if (u instanceof Stmt && ((Stmt)u).containsInvokeExpr()) {
						Stmt stmt = (Stmt) u;
						for(Value v : stmt.getInvokeExpr().getArgs()) {
							if(v instanceof Constant) {
								retMap.put(u, methodBody);
							}
						}
					}
				}
			}
		}
		return retMap;
	}

	private boolean isFieldRef(Value op) {
		return op instanceof InstanceFieldRef || op instanceof StaticFieldRef;
	}


}
