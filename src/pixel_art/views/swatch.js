import React, { Fragment } from 'react';
import tinycolor from 'tinycolor2';
import { HsvaColor, hexToHsva, color as handleColor, ColorResult } from '@uiw/color-convert';
import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
const Swatch = /*#__PURE__*/React.forwardRef((props, ref) => {
  const {
    prefixCls = 'w-color-swatch',
    className,
    color,
    colors = [],
    style,
    rectProps = {},
    onChange,
    addonAfter,
    addonBefore,
    rectRender,
    ...other
  } = props;
  const rectStyle = {
    '--swatch-background-color': 'rgb(144, 19, 254)',
    background: 'var(--swatch-background-color)',
    height: 15,
    width: 15,
    marginRight: 5,
    marginBottom: 5,
    cursor: 'pointer',
    position: 'relative',
    outline: 'none',
    borderRadius: 2,
    ...rectProps.style
  };
  const handleClick = (color, evn) => {
    onChange && onChange(color);
  };
  return /*#__PURE__*/_jsxs("div", {
    ref: ref,
    ...other,
    className: [prefixCls, className || ''].filter(Boolean).join(' '),
    style: {
      display: 'flex',
      flexWrap: 'wrap',
      position: 'relative',
      ...style
    },
    children: [addonBefore && /*#__PURE__*/React.isValidElement(addonBefore) && addonBefore, colors && Array.isArray(colors) && colors.map((item, idx) => {
      let title = '';
      let background = '';
      if (typeof item === 'string') {
        title = item;
        background = item;
      }
      if (typeof item === 'object' && item.color) {
        title = item.title || item.color;
        background = item.color;
      }
      const checked = color && tinycolor(color).toRgbString() === tinycolor(background).toRgbString();
      const render = rectRender && rectRender({
        title,
        color: background,
        checked: !!checked,
        style: {
          ...rectStyle,
          background
        },
        onClick: evn => handleClick(background, evn)
      });
      if (render) {
        return /*#__PURE__*/_jsx(Fragment, {
          children: render
        }, idx);
      }
      const child = rectProps.children && /*#__PURE__*/React.isValidElement(rectProps.children) ? React.cloneElement(rectProps.children, {
        color: background,
        checked
      }) : null;
      return /*#__PURE__*/_jsx("div", {
        tabIndex: 0,
        title: title,
        onClick: evn => handleClick(background, evn),
        ...rectProps,
        children: child,
        style: {
          ...rectStyle,
          background
        }
      }, idx);
    }), addonAfter && /*#__PURE__*/React.isValidElement(addonAfter) && addonAfter]
  });
});
Swatch.displayName = 'Swatch';
export default Swatch;