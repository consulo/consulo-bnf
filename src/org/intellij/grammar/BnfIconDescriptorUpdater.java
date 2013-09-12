package org.intellij.grammar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconDescriptor;
import com.intellij.ide.IconDescriptorUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.intellij.grammar.psi.BnfAttr;
import org.intellij.grammar.psi.BnfAttrs;
import org.intellij.grammar.psi.BnfRule;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 12.09.13.
 */
public class BnfIconDescriptorUpdater implements IconDescriptorUpdater {
	@Override
	public void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement element, int flags) {
		if (element instanceof BnfRule) {
			final Icon base = ((BnfRule) element).hasModifier("external") ? BnfIcons.EXTERNAL_RULE : BnfIcons.RULE;
			final Icon visibility = ((BnfRule) element).hasModifier("private") ? PlatformIcons.PRIVATE_ICON : PlatformIcons.PUBLIC_ICON;

			iconDescriptor.setMainIcon(base);
			iconDescriptor.setRightIcon(visibility);
		} else if (element instanceof BnfAttr) {
			iconDescriptor.setMainIcon(BnfIcons.ATTRIBUTE);
		} else if (element instanceof BnfAttrs) {
			iconDescriptor.setMainIcon(AllIcons.Nodes.Package);
		}
	}
}
