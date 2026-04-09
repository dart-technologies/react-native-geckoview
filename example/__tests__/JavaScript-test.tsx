/**
 * @format
 * JavaScript Execution Tests
 * 
 * Tests for evaluateJavaScript functionality:
 * - Basic script execution
 * - Return value handling
 * - Error handling
 * - Complex script scenarios
 */

import '../setup';

import React from 'react';
import renderer from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('JavaScript Execution Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    describe('evaluateJavaScript Method', () => {
        it('exposes evaluateJavaScript method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current).not.toBeNull();
            expect(ref.current!.evaluateJavaScript).toBeDefined();
            expect(typeof ref.current!.evaluateJavaScript).toBe('function');
        });

        it('evaluateJavaScript returns a promise', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="test-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = ref.current!.evaluateJavaScript('1 + 1');
            expect(result).toBeInstanceOf(Promise);
        });

        it('calls native module with session key and code', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="eval-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('document.title');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'eval-session',
                'document.title'
            );
        });
    });

    describe('Script Types', () => {
        it('executes simple arithmetic expression', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="math-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('2 + 2');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'math-session',
                '2 + 2'
            );
        });

        it('executes DOM query', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="dom-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('document.querySelector("body").innerHTML');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalled();
        });

        it('executes function call', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="func-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('JSON.stringify({ key: "value" })');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalled();
        });

        it('executes IIFE (Immediately Invoked Function Expression)', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="iife-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const code = '(function() { return "hello"; })()';
            await ref.current!.evaluateJavaScript(code);

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'iife-session',
                code
            );
        });

        it('executes async/await code', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="async-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const code = 'Promise.resolve("async result")';
            await ref.current!.evaluateJavaScript(code);

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'async-session',
                code
            );
        });
    });

    describe('Return Values', () => {
        it('handles string return value', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce('"hello"');

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="string-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('"hello"');
            expect(result).toBe('"hello"');
        });

        it('handles number return value', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce('42');

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="number-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('42');
            expect(result).toBe('42');
        });

        it('handles boolean return value', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce('true');

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="bool-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('true');
            expect(result).toBe('true');
        });

        it('handles object return value (as JSON)', async () => {
            const objJson = '{"key":"value","count":123}';
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce(objJson);

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="obj-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('({key: "value", count: 123})');
            expect(result).toBe(objJson);
        });

        it('handles null return value', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce('null');

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="null-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('null');
            expect(result).toBe('null');
        });

        it('handles undefined return value', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce('undefined');

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="undef-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('undefined');
            expect(result).toBe('undefined');
        });

        it('handles array return value', async () => {
            const arrJson = '[1,2,3,"four"]';
            NativeModules.GeckoViewModule.evaluateJavaScript.mockResolvedValueOnce(arrJson);

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="arr-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const result = await ref.current!.evaluateJavaScript('[1, 2, 3, "four"]');
            expect(result).toBe(arrJson);
        });
    });

    describe('Error Handling', () => {
        it('handles syntax error rejection', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockRejectedValueOnce(
                new Error('SyntaxError: Unexpected token')
            );

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="syntax-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await expect(ref.current!.evaluateJavaScript('function {')).rejects.toThrow();
        });

        it('handles ReferenceError rejection', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockRejectedValueOnce(
                new Error('ReferenceError: undefinedVar is not defined')
            );

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="ref-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await expect(ref.current!.evaluateJavaScript('undefinedVar')).rejects.toThrow();
        });

        it('handles TypeError rejection', async () => {
            NativeModules.GeckoViewModule.evaluateJavaScript.mockRejectedValueOnce(
                new Error('TypeError: null.property is not defined')
            );

            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="type-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await expect(ref.current!.evaluateJavaScript('null.property')).rejects.toThrow();
        });
    });

    describe('Multiple Executions', () => {
        it('handles sequential script executions', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="seq-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('1 + 1');
            await ref.current!.evaluateJavaScript('2 + 2');
            await ref.current!.evaluateJavaScript('3 + 3');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledTimes(3);
        });

        it('handles parallel script executions', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="par-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const promises = [
                ref.current!.evaluateJavaScript('1'),
                ref.current!.evaluateJavaScript('2'),
                ref.current!.evaluateJavaScript('3'),
            ];

            await Promise.all(promises);

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledTimes(3);
        });
    });

    describe('Special Characters', () => {
        it('handles scripts with quotes', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="quote-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('"hello" + "world"');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'quote-session',
                '"hello" + "world"'
            );
        });

        it('handles scripts with unicode', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="unicode-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            await ref.current!.evaluateJavaScript('"日本語テスト"');

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalledWith(
                'unicode-session',
                '"日本語テスト"'
            );
        });

        it('handles scripts with newlines', async () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="newline-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const multilineScript = `
                var x = 1;
                var y = 2;
                x + y;
            `;
            await ref.current!.evaluateJavaScript(multilineScript);

            expect(NativeModules.GeckoViewModule.evaluateJavaScript).toHaveBeenCalled();
        });
    });
});
