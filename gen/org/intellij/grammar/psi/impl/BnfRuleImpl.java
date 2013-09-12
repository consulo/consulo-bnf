/*
 * Copyright 2011-2011 Gregory Shrago
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
package org.intellij.grammar.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.grammar.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.intellij.grammar.psi.BnfTypes.BNF_ID;

public class BnfRuleImpl extends BnfNamedElementImpl implements BnfRule {

	public BnfRuleImpl(ASTNode node) {
		super(node);
	}

	@Override
	@Nullable
	public BnfAttrs getAttrs() {
		return findChildByClass(BnfAttrs.class);
	}

	@Override
	@NotNull
	public BnfExpression getExpression() {
		return findNotNullChildByClass(BnfExpression.class);
	}

	@Override
	@NotNull
	public List<BnfModifier> getModifierList() {
		return PsiTreeUtil.getChildrenOfTypeAsList(this, BnfModifier.class);
	}

	@Override
	public boolean hasModifier(String modifier) {
		for (BnfModifier o : getModifierList()) {
			if (modifier.equals(o.getText())) return true;
		}
		return false;
	}

	@Override
	@NotNull
	public PsiElement getId() {
		return findNotNullChildByType(BNF_ID);
	}

	public void accept(@NotNull PsiElementVisitor visitor) {
		if (visitor instanceof BnfVisitor) ((BnfVisitor) visitor).visitRule(this);
		else super.accept(visitor);
	}

}
