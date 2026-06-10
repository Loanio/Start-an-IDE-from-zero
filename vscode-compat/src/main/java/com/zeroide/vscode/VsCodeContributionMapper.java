package com.zeroide.vscode;

public final class VsCodeContributionMapper {
    public VsCodeStaticContributionPlan mapStaticContributions(VsCodeExtensionDescriptor descriptor) {
        String note = descriptor.hasExecutableEntryPoint()
                ? "This extension has a JavaScript entry point and only static contributions are supported."
                : "Static contributions are supported.";
        VsCodeContributions contributions = descriptor.contributions();
        return new VsCodeStaticContributionPlan(
                descriptor.id(),
                descriptor.hasExecutableEntryPoint(),
                note,
                contributions.commands(),
                contributions.languages(),
                contributions.grammars(),
                contributions.snippets(),
                contributions.themes()
        );
    }
}
