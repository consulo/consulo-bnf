package org.intellij.grammar;

import javax.swing.Icon;

import org.intellij.grammar.psi.BnfAttr;
import org.intellij.grammar.psi.BnfAttrs;
import org.intellij.grammar.psi.BnfModifier;
import org.intellij.grammar.psi.BnfRule;
import org.jetbrains.annotations.NotNull;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconDescriptor;
import com.intellij.ide.IconDescriptorUpdater;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;

/**
 * @author VISTALL
 * @since 12.09.13.
 */
public class BnfIconDescriptorUpdater implements IconDescriptorUpdater
{
	@Override
	public void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement element, int flags)
	{
		if(element instanceof BnfRule)
		{
			final Icon base = hasModifier((BnfRule) element, "external") ? BnfIcons.EXTERNAL_RULE : BnfIcons.RULE;
			final Icon visibility = hasModifier((BnfRule) element,"private") ? PlatformIcons.PRIVATE_ICON : PlatformIcons.PUBLIC_ICON;

			iconDescriptor.setMainIcon(base);
			iconDescriptor.setRightIcon(visibility);
		}
		else if(element instanceof BnfAttr)
		{
			iconDescriptor.setMainIcon(BnfIcons.ATTRIBUTE);
		}
		else if(element instanceof BnfAttrs)
		{
			iconDescriptor.setMainIcon(AllIcons.Nodes.Package);
		}
	}

	private static boolean hasModifier(BnfRule bnfRule, String modifier)
	{
		for(BnfModifier bnfModifier : bnfRule.getModifierList())
		{
			if(Comparing.equal(bnfModifier.getText(), modifier))
			{
				return true;
			}
		}
		return false;
	}
}
