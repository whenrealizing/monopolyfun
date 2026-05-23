import {createNavigation} from "next-intl/navigation";
import {type ComponentProps, createElement, forwardRef} from "react";

import {routing} from "@/i18n/routing";

const navigation = createNavigation(routing);
const IntlLink = navigation.Link;
type LinkProps = ComponentProps<typeof IntlLink>;

export const Link = forwardRef<HTMLAnchorElement, LinkProps>(function Link({prefetch = false, ...props}, ref) {
    // 中文注释：App Router 初始化前的 eager prefetch 会触发 Next 内部路由队列错误，默认延后到显式需要时开启。
    return createElement(IntlLink, {...props, ref, prefetch});
});
export const redirect = navigation.redirect;
export const usePathname = navigation.usePathname;
export const useRouter = navigation.useRouter;
export const getPathname = navigation.getPathname;
