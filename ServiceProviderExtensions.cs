using System;
using Microsoft.Extensions.DependencyInjection;

namespace DotNative.InAppWebView;

public static class InAppWebViewServiceProviderExtensions
{
#if NET10_0_OR_GREATER
    extension(IServiceProvider services)
    {
        /// <summary>Resolves the registered plugin using the provider's DI lifetime.</summary>
        public IInAppWebView InAppWebView => services.GetRequiredService<IInAppWebView>();
    }
#else
    /// <summary>Resolves the registered plugin using the provider's DI lifetime.</summary>
    public static IInAppWebView InAppWebView(this IServiceProvider services) =>
        services.GetRequiredService<IInAppWebView>();
#endif
}
